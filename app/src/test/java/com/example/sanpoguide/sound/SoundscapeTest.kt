package com.example.sanpoguide.sound

import com.example.sanpoguide.mood.Mood
import com.example.sanpoguide.mood.Place
import com.example.sanpoguide.mood.Season
import com.example.sanpoguide.mood.Sky
import com.example.sanpoguide.mood.TimeOfDay
import com.example.sanpoguide.station.format.SoundChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SoundscapeTest {
    private fun mood(time: TimeOfDay = TimeOfDay.DAY, season: Season = Season.SPRING, sky: Sky? = Sky.CLEAR, place: Place? = null) =
        Mood(time, season, sky, place)

    @Test
    fun `weather comes first`() {
        assertEquals(Soundscape.RAIN, Soundscape.forMood(mood(sky = Sky.RAIN, place = Place.WATERSIDE)))
        assertEquals(Soundscape.RAIN, Soundscape.forMood(mood(sky = Sky.THUNDER)))
        assertEquals(Soundscape.WIND, Soundscape.forMood(mood(sky = Sky.SNOW, place = Place.SHRINE_TEMPLE)))
    }

    @Test
    fun `then the place, then the time and season`() {
        assertEquals(Soundscape.WAVES, Soundscape.forMood(mood(time = TimeOfDay.NIGHT, place = Place.WATERSIDE)))
        assertEquals(Soundscape.INSECTS, Soundscape.forMood(mood(time = TimeOfDay.NIGHT, season = Season.AUTUMN, place = Place.PARK)))
        assertEquals(Soundscape.WIND, Soundscape.forMood(mood(time = TimeOfDay.NIGHT, season = Season.WINTER)))
        assertEquals(Soundscape.TEMPLE, Soundscape.forMood(mood(place = Place.SHRINE_TEMPLE)))
        assertEquals(Soundscape.BIRDS, Soundscape.forMood(mood(place = Place.PARK)))
        assertEquals(Soundscape.BIRDS, Soundscape.forMood(mood(sky = null)))
        assertEquals(Soundscape.WIND, Soundscape.forMood(mood(place = Place.TOWN)))
        assertEquals(Soundscape.WIND, Soundscape.forMood(mood(season = Season.WINTER, place = Place.PARK)))
    }

    @Test
    fun `a channel can fix the sound or leave it to the mood`() {
        val rainyTemple = mood(sky = Sky.RAIN, place = Place.SHRINE_TEMPLE)
        assertEquals(Soundscape.RAIN, Soundscape.of(SoundChoice.AUTO, rainyTemple))
        assertEquals(Soundscape.TEMPLE, Soundscape.of(SoundChoice.TEMPLE, rainyTemple))
        assertEquals(Soundscape.BIRDS, Soundscape.of(SoundChoice.BIRDS, mood(time = TimeOfDay.NIGHT)))
    }
}

class VoicesTest {
    private val rate = 22_050

    private fun render(scape: Soundscape, seconds: Int, seed: Long = 42): FloatArray {
        val v = Voices.create(scape, rate, seed)
        return FloatArray(rate * seconds) { v.next() }
    }

    @Test
    fun `every sound stays in range, is audible and isn't stuck to one side`() {
        Soundscape.entries.forEach { scape ->
            // The temple bell's first strike comes after a few seconds.
            val samples = render(scape, seconds = 10)
            // Well under full scale: the final clamp is a guard, not part of the sound.
            assertTrue("$scape clips or is NaN", samples.all { !it.isNaN() && abs(it) < 0.95f })
            val rms = sqrt(samples.map { it * it }.average())
            assertTrue("$scape is silent (rms $rms)", rms > 0.003)
            assertTrue("$scape is too loud (rms $rms)", rms < 0.5)
            val mean = samples.average()
            assertTrue("$scape has a DC offset ($mean)", abs(mean) < 0.05)
        }
    }

    @Test
    fun `the same seed makes the same sound`() {
        assertTrue(render(Soundscape.BIRDS, 2, seed = 7).contentEquals(render(Soundscape.BIRDS, 2, seed = 7)))
    }
}
