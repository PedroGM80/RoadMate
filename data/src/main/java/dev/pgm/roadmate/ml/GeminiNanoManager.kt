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
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * What the last attempt told us about this hardware.
     *
     * Deliberately three-valued. The old code collapsed "timed out" and
     * "AICore isn't on this device" into a single cached `false`, so one slow
     * first call — a cold AICore service, or the model still being staged by
     * Play Services — permanently demoted a capable phone to "modo básico"
     * for the whole session, with no way back short of a restart. Only a
     * thrown call proves absence; a timeout leaves the question open so the
     * next question can ask again.
     */
    private enum class Availability { AVAILABLE, ABSENT }

    @Volatile
    private var known: Availability? = null

    /** True/false once AICore has proved either way, null while inconclusive. */
    private val isAvailable: Boolean? get() = known?.let { it == Availability.AVAILABLE }

    suspend fun generateResponse(prompt: String): String {
        dbg("PROMPT >>>\n$prompt")
        val outcome = attempt(prompt)
        dbg("RAW RESPONSE <<< ${outcome.text?.let { "\"$it\"" } ?: "null (${outcome.reason})"}")
        Log.d(TAG, "RAW RESPONSE <<<\n${outcome.text}")
        return outcome.text?.takeIf { it.isNotBlank() } ?: FALLBACK_RESPONSE
    }

    /**
     * Whether on-device Gemini Nano actually works on this hardware.
     *
     * Called once at startup so the UI can be upfront about running in "modo
     * básico" instead of silently returning FALLBACK_RESPONSE on every
     * question with no explanation — AICore is only on a handful of devices
     * today (confirmed missing on a plain emulator: "AiCoreService: not
     * found"). A definite answer is cached; an inconclusive one (timeout) is
     * reported as "not right now" but left open for the next call.
     */
    suspend fun checkAvailability(): Boolean {
        isAvailable?.let { return it }
        val outcome = attempt(PROBE_PROMPT)
        dbg("PROBE -> ${outcome.text?.let { "\"$it\"" } ?: outcome.reason}")
        return outcome.text != null
    }

    private class Outcome(val text: String?, val reason: String)

    /**
     * One call to AICore, recording what it proved. A throw means the service
     * isn't there (or refused) — that is durable. A timeout means only that
     * this attempt was too slow.
     */
    private suspend fun attempt(prompt: String): Outcome {
        var threw: Throwable? = null
        val text = withTimeoutOrNull(Constants.GEMINI_TIMEOUT_MS.milliseconds) {
            runCatching { model.generateContent(prompt).text }
                .onFailure {
                    threw = it
                    dbg("generateContent FAILED: ${it.stackTraceToString()}")
                }
                .getOrNull()
        }
        known = when {
            text != null -> Availability.AVAILABLE
            threw != null -> Availability.ABSENT
            else -> known // timed out: still unknown, ask again next time
        }
        return Outcome(text, if (threw != null) "unavailable" else "timeout")
    }

    companion object {
        private const val TAG = "GeminiNanoManager"

        /** Cheapest thing that still proves the whole path works end to end. */
        private const val PROBE_PROMPT = "ok"

        /** Shared with [dev.pgm.roadmate.data.repository.GeminiRepositoryImpl]
         *  as the last-resort "modo básico" answer when no local backend
         *  (AICore or the downloaded model) can respond. */
        internal const val FALLBACK_RESPONSE =
            "Ahora no puedo responder a eso. Pregúntamelo otra vez en un rato."
    }
}
