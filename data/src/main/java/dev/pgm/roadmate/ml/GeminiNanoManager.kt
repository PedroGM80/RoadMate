package dev.pgm.roadmate.ml

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.withTimeoutOrNull
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
        val response = withTimeoutOrNull(Constants.GEMINI_TIMEOUT_MS) {
            runCatching { model.generateContent(prompt).text }.getOrNull()
        }
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
            runCatching { model.generateContent("ok").text }.getOrNull()
        }
        return (probe != null).also { lastKnownAvailable = it }
    }

    companion object {
        /** Shared with [dev.pgm.roadmate.data.repository.GeminiRepositoryImpl]
         *  as the last-resort "modo básico" answer when no local backend
         *  (AICore or the downloaded model) can respond. */
        internal const val FALLBACK_RESPONSE =
            "No he podido generar una respuesta ahora mismo. Puedes repetir la pregunta más adelante."
    }
}
