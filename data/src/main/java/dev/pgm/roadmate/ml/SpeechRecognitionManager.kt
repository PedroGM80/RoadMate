package dev.pgm.roadmate.ml

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Transcribes speech on-device using Android's SpeechRecognizer with the
 * offline-preferred flag set, so recognition works without network access
 * when an offline language pack is installed.
 *
 * Note: ML Kit does not expose a general-purpose speech-to-text API; this
 * wraps the platform SpeechRecognizer, whose RecognitionListener callback
 * shape matches what was requested.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit = {},
    private val onError: (Int) -> Unit = {},
    private val onPartialResult: (String) -> Unit = {}
) {

    private var speechRecognizer: SpeechRecognizer? = null

    val isListening: Boolean
        get() = speechRecognizer != null

    fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            onError.invoke(error)
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            onResult(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialTranscript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partialTranscript.isNullOrBlank()) {
                onPartialResult(partialTranscript)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
