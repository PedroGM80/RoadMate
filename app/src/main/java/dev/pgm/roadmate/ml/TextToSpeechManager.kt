package dev.pgm.roadmate.ml

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays generated responses as speech using a single shared TTS engine instance.
 * Utterances requested before the engine finishes initializing are queued and
 * flushed once it becomes ready.
 *
 * Audio is tagged USAGE_ASSISTANT / CONTENT_TYPE_SPEECH so it's routed through the
 * assistant channel (and, in a car, the head unit's speech output) rather than the
 * media stream. The app cannot force the vehicle's physical volume — if it's muted
 * or turned down, that's outside what AudioAttributes can control; callers should
 * pair speech with the UI's visual state (see RoadMateStatus.SPEAKING) as a fallback.
 */
@Singleton
class TextToSpeechManager @Inject constructor(@ApplicationContext context: Context) {

    private var isReady = false
    private val pendingUtterances = mutableListOf<Pair<String, () -> Unit>>()
    private val doneCallbacks = mutableMapOf<String, () -> Unit>()

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
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
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { doneCallbacks.remove(it) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { doneCallbacks.remove(it) }
                    }
                })

                val queued = pendingUtterances.toList()
                pendingUtterances.clear()
                queued.forEach { (text, onDone) -> enqueue(text, onDone) }
            }
        }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) {
            onDone()
            return
        }
        if (!isReady) {
            pendingUtterances.add(text to onDone)
            return
        }
        enqueue(text, onDone)
    }

    fun stop() {
        engine.stop()
    }

    private fun enqueue(text: String, onDone: () -> Unit) {
        val utteranceId = UUID.randomUUID().toString()
        doneCallbacks[utteranceId] = onDone
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
}
