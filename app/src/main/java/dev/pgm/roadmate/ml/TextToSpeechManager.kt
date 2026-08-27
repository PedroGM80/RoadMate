package dev.pgm.roadmate.ml

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

/**
 * Plays generated responses as speech using a single shared TTS engine instance.
 * Utterances requested before the engine finishes initializing are queued and
 * flushed once it becomes ready.
 */
class TextToSpeechManager private constructor(context: Context) {

    private var isReady = false
    private val pendingUtterances = mutableListOf<String>()
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                engine.language = Locale.getDefault()
                val queued = pendingUtterances.toList()
                pendingUtterances.clear()
                queued.forEach(::enqueue)
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingUtterances.add(text)
            return
        }
        enqueue(text)
    }

    fun stop() {
        engine.stop()
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
        isReady = false
        instance = null
    }

    private fun enqueue(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    companion object {
        @Volatile
        private var instance: TextToSpeechManager? = null

        fun getInstance(context: Context): TextToSpeechManager =
            instance ?: synchronized(this) {
                instance ?: TextToSpeechManager(context.applicationContext).also { instance = it }
            }
    }
}
