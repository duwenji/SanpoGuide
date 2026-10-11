package io.github.duwenji.sanpoguide.sound

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/**
 * Plays a [Soundscape] quietly under the walk, on its own thread.
 *
 * - Fades out, then in, when the soundscape changes.
 * - Drops to [DUCK] while the companion speaks ([speaking]); audio focus can't do this, since
 *   the voice and this sound come from the same app.
 * - Stays silent while another app plays music or video, and (by default) unless earphones are
 *   connected. Both are checked every [CHECK_MS].
 */
class AmbientPlayer(context: Context, private val speaking: StateFlow<Boolean>) {
    private val audio = context.getSystemService(AudioManager::class.java)

    @Volatile private var wanted: Soundscape? = null
    @Volatile private var volume = 0f
    @Volatile private var earphonesOnly = true
    @Volatile private var running = true
    /** Set when earphones come or go, so the check doesn't wait for [CHECK_MS]. */
    @Volatile private var devicesChanged = true

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { devicesChanged = true }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { devicesChanged = true }
    }

    init {
        audio.registerAudioDeviceCallback(deviceCallback, null)
    }

    private val worker = thread(name = "ambient", isDaemon = true) {
        try {
            loop()
        } catch (e: Exception) {
            Log.w(TAG, "Background sound stopped", e)
        }
    }

    /** [scape] null for silence; [volumePercent] 0..100. Takes effect with a fade. */
    fun set(scape: Soundscape?, volumePercent: Int, earphonesOnly: Boolean) {
        wanted = scape
        // Loudness is heard roughly logarithmically; squaring spreads the slider more evenly.
        val v = volumePercent.coerceIn(0, 100) / 100f
        volume = v * v * MAX_GAIN
        this.earphonesOnly = earphonesOnly
    }

    fun release() {
        running = false
        audio.unregisterAudioDeviceCallback(deviceCallback)
        worker.join(1_000)
    }

    private fun loop() {
        val bufferBytes = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            SAMPLE_RATE / 5 * 2,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // Marks our own sound, so it isn't taken for another app's music (see otherMediaPlaying).
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val pcm = ShortArray(SAMPLE_RATE / 20) // 50 ms
        var playing: Soundscape? = null
        var voice: Voice? = null
        var level = 0f
        var allowed = false
        var checkedAt = 0L
        // Per-sample smoothing: about half a second to settle.
        val smooth = 1f / (SAMPLE_RATE * 0.4f)

        try {
            while (running) {
                val now = SystemClock.elapsedRealtime()
                if (devicesChanged || now - checkedAt >= CHECK_MS) {
                    devicesChanged = false
                    val earphonesOk = !earphonesOnly || earphonesConnected()
                    // Earphones pulled out: cut at once rather than fade out of the speaker.
                    if (!earphonesOk) level = 0f
                    allowed = earphonesOk && !otherMediaPlaying()
                    checkedAt = now
                }
                val target = wanted.takeIf { allowed }
                // Fade the current sound out before switching to the next one.
                if (target != playing && level < SILENT) {
                    playing = target
                    voice = target?.let { Voices.create(it, SAMPLE_RATE) }
                }
                val v = voice
                if (v == null) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    Thread.sleep(IDLE_MS)
                    continue
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                val goal = if (target == playing) volume * (if (speaking.value) DUCK else 1f) else 0f
                for (i in pcm.indices) {
                    level += (goal - level) * smooth
                    pcm[i] = (v.next() * level * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                // Blocks until there's room, which paces the loop.
                track.write(pcm, 0, pcm.size)
            }
        } finally {
            track.release()
        }
    }

    private fun earphonesConnected(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in EARPHONES }

    /** Another app playing music or video (our own sound is marked as sonification, the voice as guidance). */
    private fun otherMediaPlaying(): Boolean = audio.activePlaybackConfigurations.any {
        val a = it.audioAttributes
        (a.usage == AudioAttributes.USAGE_MEDIA || a.usage == AudioAttributes.USAGE_GAME) &&
            a.contentType != AudioAttributes.CONTENT_TYPE_SONIFICATION
    }

    private companion object {
        const val TAG = "AmbientPlayer"
        const val SAMPLE_RATE = 22_050
        const val MAX_GAIN = 0.8f
        const val DUCK = 0.2f
        const val SILENT = 0.002f
        const val CHECK_MS = 2_000L
        const val IDLE_MS = 500L

        // Hearing aids and BLE headsets (API 28 / 31) are plain ints, safe to list on older versions.
        @SuppressLint("InlinedApi")
        val EARPHONES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
