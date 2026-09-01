package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends prompts to the on-device Gemini Nano model via Android AICore and returns
 * the generated text, or a canned offline fallback if AICore times out or fails
 * (model not downloaded yet, device unsupported, inference error).
 *
 * Requires `com.google.ai.edge.aicore:aicore` (declared in the version catalog).
 * The SDK is an early-access preview — verify class/package names against the
 * AICore release in use before shipping.
 */
@Singleton
class GeminiNanoManager @Inject constructor(@ApplicationContext context: Context) {

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
    }

    private fun dbg(line: String) = DebugTrace.log("NANO $line")

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            generationConfig = generationConfig {
                this.context = context
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 256
            }
        )
    }

    @Volatile
    private var lastKnownAvailable: Boolean? = null

    suspend fun generateResponse(prompt: String): String {
        dbg("PROMPT >>>\n$prompt")
        val response = withTimeoutOrNull(Constants.GEMINI_TIMEOUT_MS) {
            runCatching { model.generateContent(prompt).text }
                .onFailure { dbg("generateContent FAILED: ${it.stackTraceToString()}") }
                .getOrNull()
        }
        dbg("RAW RESPONSE <<< ${response?.let { "\"$it\"" } ?: "null (timeout/exception)"}")
        Log.d(TAG, "RAW RESPONSE <<<\n$response")
        lastKnownAvailable = response != null
        return response?.takeIf { it.isNotBlank() } ?: FALLBACK_RESPONSE
    }

    /**
     * Whether on-device Gemini Nano actually works on this hardware, cached
     * after the first attempt (real question or this probe). Meant to be
     * called once at startup so the UI can be upfront about running in
     * "modo básico" instead of silently returning FALLBACK_RESPONSE on every
     * question with no explanation — AICore is only on a handful of devices
     * today (confirmed missing on a plain emulator: "AiCoreService: not found").
     */
    suspend fun checkAvailability(): Boolean {
        lastKnownAvailable?.let { return it }
        val probe = withTimeoutOrNull(Constants.GEMINI_TIMEOUT_MS) {
            runCatching { model.generateContent("ok").text }
                .onFailure { dbg("PROBE FAILED: ${it.stackTraceToString()}") }
                .getOrNull()
        }
        dbg("PROBE 'ok' -> ${probe?.let { "\"$it\"" } ?: "null"}")
        return (probe != null).also { lastKnownAvailable = it }
    }

    companion object {
        private const val TAG = "GeminiNanoManager"

        /** Shared with [dev.pgm.roadmate.data.repository.GeminiRepositoryImpl]
         *  as the last-resort "modo básico" answer when no local backend
         *  (AICore or the downloaded model) can respond. */
        internal const val FALLBACK_RESPONSE =
            "Ahora no puedo responder a eso. Pregúntamelo otra vez en un rato."
    }
}
