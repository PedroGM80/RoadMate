package dev.pgm.roadmate.ml

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays generated responses as speech using a single shared TTS engine instance.
 * Utterances requested before the engine finishes initializing are queued and
 * flushed once it becomes ready.
 *
 * [isSpeaking] lets the recogniser hold off opening the mic while RoadMate is
 * still talking — otherwise it transcribes its own voice and answers it.
 */
@Singleton
class TextToSpeechManager @Inject constructor(@ApplicationContext context: Context) {

    private var isReady = false
    private val pendingUtterances = mutableListOf<Pair<String, () -> Unit>>()
    private val doneCallbacks = mutableMapOf<String, () -> Unit>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            setupEngine()
            flushPending()
        }
    }

    private fun setupEngine() {
        engine.language = Locale.getDefault()
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { doneCallbacks.remove(it)?.invoke() }
                refreshSpeakingState()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { doneCallbacks.remove(it) }
                refreshSpeakingState()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { doneCallbacks.remove(it) }
                refreshSpeakingState()
            }
        })
    }

    private fun flushPending() {
        val queued = pendingUtterances.toList()
        pendingUtterances.clear()
        queued.forEach { (text, onDone) -> enqueue(text, onDone) }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) {
            onDone()
            return
        }
        if (!isReady) {
            pendingUtterances.add(text to onDone)
            _isSpeaking.value = true
            return
        }
        enqueue(text, onDone)
    }

    fun stop() {
        engine.stop()
        pendingUtterances.clear()
        doneCallbacks.clear()
        _isSpeaking.value = false
    }

    private fun enqueue(text: String, onDone: () -> Unit) {
        val utteranceId = UUID.randomUUID().toString()
        doneCallbacks[utteranceId] = onDone
        _isSpeaking.value = true
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    @Synchronized
    private fun refreshSpeakingState() {
        _isSpeaking.value = doneCallbacks.isNotEmpty() || pendingUtterances.isNotEmpty()
    }
}
