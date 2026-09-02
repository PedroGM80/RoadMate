package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.utils.SpokenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline Spanish speech-to-text via Vosk (Kaldi). Unlike Android's
 * `SpeechRecognizer` + `EXTRA_PREFER_OFFLINE`, this does **not** depend on a
 * Google speech pack being installed — the ~39 MB model is bundled in the
 * app's assets and loaded once by [VoskModelProvider].
 *
 * [recognize] streams live [SpeechRecognitionEvent.Partial]s while the user
 * speaks, then one [SpeechRecognitionEvent.Result] (at the first pause) or
 * [SpeechRecognitionEvent.Failed].
 */
@Singleton
class VoskSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelProvider: VoskModelProvider,
) {

    private val carMicrophonePreference = CarMicrophonePreference(context)

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
        // Warm the model so the first mic tap isn't a 1–2 s cold load.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { modelProvider.get() } }
    }

    fun recognize(): Flow<SpeechRecognitionEvent> = callbackFlow {
        val loadedModel = runCatching { modelProvider.get() }.getOrNull()
        if (loadedModel == null) {
            trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_NOT_READY))
            close()
            return@callbackFlow
        }

        carMicrophonePreference.preferCarMicrophoneIfAvailable()
        val startedAt = System.currentTimeMillis()
        DebugTrace.log("STT listening…")

        val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val text = hypothesis.field("partial")
                if (text.isNotBlank()) {
                    DebugTrace.log("STT partial: \"$text\"")
                    trySend(SpeechRecognitionEvent.Partial(text))
                }
            }

            override fun onResult(hypothesis: String?) {
                val text = hypothesis.field("text")
                DebugTrace.log("STT RESULT (${System.currentTimeMillis() - startedAt} ms): \"$text\"")
                trySend(SpeechRecognitionEvent.Result(text))
                close()
            }

            override fun onFinalResult(hypothesis: String?) {
                val text = hypothesis.field("text")
                DebugTrace.log("STT finalResult: \"$text\"")
                if (trySend(SpeechRecognitionEvent.Result(text)).isSuccess) close()
            }

            override fun onError(exception: Exception?) {
                Log.w(TAG, "recognition error", exception)
                DebugTrace.log("STT error: ${exception?.message}")
                trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_FLOW_ERROR))
                close()
            }

            override fun onTimeout() {
                DebugTrace.log("STT timeout (no speech)")
                trySend(SpeechRecognitionEvent.Result(""))
                close()
            }
        }

        val speechService = try {
            SpeechService(recognizer, SAMPLE_RATE)
        } catch (e: IOException) {
            Log.w(TAG, "could not open microphone", e)
            trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_MIC_DENIED))
            recognizer.close()
            carMicrophonePreference.clearPreference()
            close()
            return@callbackFlow
        }

        speechService.startListening(listener, MAX_UTTERANCE_MS)

        awaitClose {
            runCatching { speechService.stop() }
            runCatching { speechService.shutdown() }
            runCatching { recognizer.close() }
            carMicrophonePreference.clearPreference()
        }
    }

    private fun String?.field(name: String): String =
        if (this.isNullOrBlank()) "" else runCatching { JSONObject(this).optString(name).trim() }.getOrDefault("")

    private companion object {
        const val SAMPLE_RATE = 16000.0f
        const val MAX_UTTERANCE_MS = 12_000
        const val TAG = "VoskSpeechRecognizer"
    }
}
