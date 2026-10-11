package io.github.duwenji.sanpoguide.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** An endless mono sound, one sample at a time in -1..1. Pure Kotlin, so it can be tested off the device. */
fun interface Voice {
    fun next(): Float
}

/**
 * The [Soundscape]s, built from filtered noise and a few oscillators. Each voice is seeded, so a
 * test hears the same thing every time; on the phone the seed changes per walk.
 */
object Voices {
    fun create(scape: Soundscape, sampleRate: Int, seed: Long = System.nanoTime()): Voice {
        val sr = sampleRate.toFloat()
        val rng = Rng(seed)
        val voice = when (scape) {
            Soundscape.RAIN -> rain(sr, rng)
            Soundscape.WAVES -> waves(sr, rng)
            Soundscape.WIND -> wind(sr, rng, gain = 1f)
            Soundscape.BIRDS -> mix(wind(sr, rng, gain = 0.25f), birds(sr, rng))
            Soundscape.INSECTS -> mix(wind(sr, rng, gain = 0.15f), insects(sr, rng))
            Soundscape.TEMPLE -> mix(wind(sr, rng, gain = 0.5f), bell(sr, rng))
        }
        // The levels are tuned to about 0.1 RMS for the steady sounds; this only guards the rare peak.
        return Voice { voice.next().coerceIn(-1f, 1f) }
    }

    private fun mix(vararg voices: Voice) = Voice { var sum = 0f; for (v in voices) sum += v.next(); sum.coerceIn(-1f, 1f) }

    /** A steady hiss of rain plus the odd close drop. */
    private fun rain(sr: Float, rng: Rng): Voice {
        val high = OnePole(sr, 3200f)
        val low = OnePole(sr, 500f)
        val dropTone = OnePole(sr, 2500f)
        var drop = 0f
        val dropDecay = decay(sr, 0.004f)
        val dropsPerSample = 14f / sr
        return Voice {
            val band = high.lp(rng.next())
            val bed = (band - low.lp(band)) * 0.35f
            if (rng.unit() < dropsPerSample) drop = 0.15f + rng.unit() * 0.35f
            drop *= dropDecay
            val d = rng.next()
            bed + (d - dropTone.lp(d)) * drop * 0.7f
        }
    }

    /** Surf rolling in every 7 to 11 seconds: noise that swells, brightens and fades. */
    private fun waves(sr: Float, rng: Rng): Voice {
        val brown = Brown()
        val tone = OnePole(sr, 400f)
        var t = 0f
        var period = 9f
        return Voice {
            t += 1f / sr
            if (t >= period) { t = 0f; period = 7f + rng.unit() * 4f }
            val x = t / period
            // Rises over the first third, falls away slowly.
            val swell = if (x < 0.35f) sin(x / 0.35f * PI.toFloat() / 2) else exp(-(x - 0.35f) * 4.5f)
            tone.setCutoff(sr, 250f + 1600f * swell)
            tone.lp(brown.next(rng)) * (0.12f + 0.75f * swell)
        }
    }

    /** Low noise whose strength drifts, like gusts. */
    private fun wind(sr: Float, rng: Rng, gain: Float): Voice {
        val brown = Brown()
        val high = OnePole(sr, 900f)
        val low = OnePole(sr, 120f)
        var level = 0.5f
        var target = 0.5f
        var untilChange = 0
        val drift = 1f / (sr * 1.5f)
        return Voice {
            if (--untilChange <= 0) { target = 0.2f + rng.unit() * 0.8f; untilChange = (sr * (2f + rng.unit() * 3f)).toInt() }
            level += (target - level) * drift
            val band = high.lp(brown.next(rng))
            (band - low.lp(band)) * level * gain * 1.35f
        }
    }

    /** Short phrases of rising and falling chirps, now and then. */
    private fun birds(sr: Float, rng: Rng): Voice {
        var wait = (sr * 1.5f).toInt()
        var chirpsLeft = 0
        var chirpLen = 0
        var chirpPos = 0
        var gap = 0
        var f0 = 3000f
        var f1 = 3500f
        var amp = 0f
        var phase = 0f
        return Voice {
            when {
                chirpPos < chirpLen -> {
                    val x = chirpPos.toFloat() / chirpLen
                    val f = f0 + (f1 - f0) * x
                    phase += 2 * PI.toFloat() * f / sr
                    if (phase > 2 * PI) phase -= 2 * PI.toFloat()
                    chirpPos++
                    if (chirpPos == chirpLen) gap = (sr * (0.04f + rng.unit() * 0.08f)).toInt()
                    sin(phase) * sin(x * PI.toFloat()) * amp
                }
                gap > 0 -> { gap--; 0f }
                chirpsLeft > 0 -> {
                    chirpsLeft--
                    chirpLen = (sr * (0.05f + rng.unit() * 0.08f)).toInt()
                    chirpPos = 0
                    f0 = f1.coerceIn(2400f, 5000f) * (0.9f + rng.unit() * 0.2f)
                    f1 = f0 * (0.7f + rng.unit() * 0.7f)
                    0f
                }
                --wait <= 0 -> {
                    // A new phrase from a new bird: its own pitch and loudness.
                    chirpsLeft = 2 + (rng.unit() * 5).toInt()
                    f1 = 2600f + rng.unit() * 2000f
                    amp = 0.13f + rng.unit() * 0.2f
                    wait = (sr * (1f + rng.unit() * 4f)).toInt()
                    0f
                }
                else -> 0f
            }
        }
    }

    /** A few crickets, each trilling at its own pitch and pace. */
    private fun insects(sr: Float, rng: Rng): Voice {
        class Cricket(val freq: Float, val cycle: Float, val amp: Float) {
            var phase = 0f
            var t = rng.unit() * cycle
            val gate = OnePole(sr, 200f)
        }
        val crickets = List(3) {
            Cricket(freq = 3800f + rng.unit() * 1400f, cycle = 0.45f + rng.unit() * 0.5f, amp = 0.07f + rng.unit() * 0.09f)
        }
        val pulse = 0.018f
        return Voice {
            var sum = 0f
            for (c in crickets) {
                c.t += 1f / sr
                if (c.t >= c.cycle) c.t -= c.cycle
                // Three pulses, then quiet for the rest of the cycle.
                val on = c.t < pulse * 6 && (c.t / pulse).toInt() % 2 == 0
                val env = c.gate.lp(if (on) 1f else 0f)
                c.phase += 2 * PI.toFloat() * c.freq / sr
                if (c.phase > 2 * PI) c.phase -= 2 * PI.toFloat()
                sum += sin(c.phase) * env * c.amp
            }
            sum
        }
    }

    /** A temple bell far away: a low, slowly beating hum struck every minute or so. */
    private fun bell(sr: Float, rng: Rng): Voice {
        // Partials of a large bell: (ratio to the hum, loudness, seconds to fade).
        val partials = listOf(Triple(1f, 1f, 9f), Triple(1.006f, 0.7f, 9f), Triple(2.02f, 0.5f, 6f),
            Triple(2.74f, 0.35f, 4.5f), Triple(4.07f, 0.2f, 3f), Triple(5.4f, 0.12f, 2f))
        val base = 110f
        val phases = FloatArray(partials.size)
        val envs = FloatArray(partials.size)
        val decays = FloatArray(partials.size) { decay(sr, partials[it].third) }
        var wait = (sr * 6f).toInt()
        return Voice {
            if (--wait <= 0) {
                for (i in envs.indices) envs[i] = partials[i].second
                wait = (sr * (45f + rng.unit() * 30f)).toInt()
            }
            var sum = 0f
            for (i in partials.indices) {
                if (envs[i] < 1e-4f) continue
                phases[i] += 2 * PI.toFloat() * base * partials[i].first / sr
                if (phases[i] > 2 * PI) phases[i] -= 2 * PI.toFloat()
                envs[i] *= decays[i]
                sum += sin(phases[i]) * envs[i]
            }
            sum * 0.22f
        }
    }

    /** Per-sample factor that fades to 1/e over [seconds]. */
    private fun decay(sr: Float, seconds: Float) = exp(-1f / (sr * seconds))
}

/** xorshift noise; fast, and repeatable for a seed. */
internal class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    /** -1..1 */
    fun next(): Float {
        s = s xor (s shl 13)
        s = s xor (s ushr 7)
        s = s xor (s shl 17)
        return ((s ushr 40).toFloat() / (1 shl 24)) * 2f - 1f
    }

    /** 0..1 */
    fun unit(): Float = (next() + 1f) / 2f
}

/** One-pole low-pass filter; subtract its output from the input for a high-pass. */
internal class OnePole(sr: Float, cutoff: Float) {
    private var a = coef(sr, cutoff)
    private var y = 0f

    fun setCutoff(sr: Float, cutoff: Float) {
        a = coef(sr, cutoff)
    }

    fun lp(x: Float): Float {
        y += a * (x - y)
        return y
    }

    private fun coef(sr: Float, cutoff: Float) = 1f - exp(-2f * PI.toFloat() * cutoff / sr)
}

/** Brown (red) noise: deep and soft, the base of wind and surf. */
internal class Brown {
    private var y = 0f

    fun next(rng: Rng): Float {
        y = (y + 0.02f * rng.next()) / 1.02f
        // Keep it from wandering off to one side.
        if (abs(y) > 1f) y *= 0.5f
        return y * 3.5f
    }
}
