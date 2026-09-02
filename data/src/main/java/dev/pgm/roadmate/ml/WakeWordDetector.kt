package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands-free activation with no extra engine, account, or cost: a Vosk
 * [Recognizer] restricted to a tiny grammar so it only ever "hears" the wake
 * phrase ("oye copiloto") or `[unk]` for everything else. It runs on the same
 * bundled Spanish model as dictation ([VoskModelProvider]).
 *
 * The wake phrase has to be built from words the Spanish model actually
 * knows — "RoadMate" isn't in the lexicon, "oye copiloto" is.
 *
 * Like Vosk dictation this holds an `AudioRecord` via [SpeechService], so the
 * collector must stop it before [VoskSpeechRecognizer] opens the mic for the
 * real question.
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelProvider: VoskModelProvider,
) {

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
    }

    /**
     * Always true — the model is bundled, so hands-free is available on every
     * device. (A user-facing off switch is a separate setting, TODO.) If the
     * model fails to load at runtime, [detections] just completes and callers
     * fall back to the mic button.
     */
    fun isConfigured(): Boolean = true

    fun detections(): Flow<Unit> = callbackFlow {
        val model = runCatching { modelProvider.get() }.getOrNull()
        if (model == null) {
            DebugTrace.log("wake: Vosk model unavailable — mic button only")
            close()
            return@callbackFlow
        }

        val recognizer = try {
            Recognizer(model, SAMPLE_RATE, GRAMMAR)
        } catch (e: IOException) {
            Log.w(TAG, "wake recognizer init failed", e)
            DebugTrace.log("wake: recognizer init failed: ${e.message}")
            close()
            return@callbackFlow
        }

        // Vosk keeps the accumulated hypothesis across partials, so once
        // "copiloto" appears every subsequent partial still contains it. Without
        // this guard one spoken phrase fired the wake handler several times in a
        // row (earcon, "Sí, dime.", mic open — repeatedly). Re-arm only after a
        // quiet gap, i.e. a hypothesis that no longer mentions the phrase.
        var armed = true
        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                update(hypothesis.mentionsWakePhrase("partial"))
            }

            override fun onResult(hypothesis: String?) {
                update(hypothesis.mentionsWakePhrase("text"))
                // A final result ends the utterance: the next one starts clean.
                armed = true
            }

            override fun onFinalResult(hypothesis: String?) {
                update(hypothesis.mentionsWakePhrase("text"))
                armed = true
            }

            override fun onError(exception: Exception?) {
                Log.w(TAG, "wake recognition error", exception)
                DebugTrace.log("wake: error: ${exception?.message}")
                close()
            }

            override fun onTimeout() {
                armed = true
            }

            private fun update(heard: Boolean) {
                if (!heard) return
                if (!armed) return
                armed = false
                DebugTrace.log("wake: heard the wake phrase")
                trySend(Unit)
            }
        }

        val speechService = try {
            SpeechService(recognizer, SAMPLE_RATE)
        } catch (e: IOException) {
            Log.w(TAG, "could not open microphone for wake word", e)
            DebugTrace.log("wake: mic open failed: ${e.message}")
            runCatching { recognizer.close() }
            close()
            return@callbackFlow
        }

        // No timeout: listen until the collector stops us.
        speechService.startListening(listener)
        DebugTrace.log("wake: listening for \"$WAKE_PHRASE\"")

        awaitClose {
            runCatching { speechService.stop() }
            runCatching { speechService.shutdown() }
            runCatching { recognizer.close() }
            DebugTrace.log("wake: stopped")
        }
    }

    private fun String?.mentionsWakePhrase(field: String): Boolean {
        if (this.isNullOrBlank()) return false
        val text = runCatching { JSONObject(this).optString(field) }.getOrDefault("")
        return text.contains(WAKE_TOKEN, ignoreCase = true)
    }

    private companion object {
        const val TAG = "WakeWordDetector"
        const val SAMPLE_RATE = 16000.0f
        const val WAKE_PHRASE = "oye copiloto"

        /** The distinctive half of the phrase — matched even if "oye" is dropped. */
        const val WAKE_TOKEN = "copiloto"

        /** Vosk grammar: only the phrase or out-of-vocabulary noise. */
        const val GRAMMAR = "[\"$WAKE_PHRASE\", \"[unk]\"]"
    }
}
