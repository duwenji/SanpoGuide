package io.github.duwenji.sanpoguide.guide

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Japanese text-to-speech. Utterances queue up rather than cutting each other off.
 *
 * While it talks it holds transient, "may duck" audio focus, so music the user is listening to
 * gets quieter instead of playing over the voice, and comes back when the queue is done.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var ready = false
    private val pending = mutableListOf<String>()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    /** Utterances handed to the engine and not finished yet; a late callback for a stopped one finds nothing. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val nextId = AtomicInteger(0)

    private val _speaking = MutableStateFlow(false)
    /** True from the start of the first queued line until the last one ends (for ducking our own background sound). */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.JAPAN
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        ready = true
        pending.forEach { enqueue(it) }
        pending.clear()
    }

    fun speak(text: String) {
        if (ready) enqueue(text) else pending += text
    }

    val isSpeaking: Boolean get() = pending.isNotEmpty() || tts.isSpeaking

    fun stop() {
        pending.clear()
        tts.stop()
        release()
    }

    private fun enqueue(text: String) {
        val id = nextId.incrementAndGet().toString()
        if (inFlight.isEmpty()) {
            audio.requestAudioFocus(focus)
            _speaking.value = true
        }
        inFlight += id
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) finished(id)
    }

    private fun finished(id: String?) {
        if (inFlight.remove(id) && inFlight.isEmpty()) release()
    }

    private fun release() {
        inFlight.clear()
        audio.abandonAudioFocusRequest(focus)
        _speaking.value = false
    }
}
