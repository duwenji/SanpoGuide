package com.example.sanpoguide.guide

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Japanese text-to-speech. Utterances queue up rather than cutting each other off. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.JAPAN
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
    }

    private fun enqueue(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }
}
