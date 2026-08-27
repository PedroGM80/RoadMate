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

    suspend fun generateResponse(prompt: String): String {
        val response = withTimeoutOrNull(Constants.GEMINI_TIMEOUT_MS) {
            runCatching { model.generateContent(prompt).text }.getOrNull()
        }
        return response?.takeIf { it.isNotBlank() } ?: FALLBACK_RESPONSE
    }

    private companion object {
        const val FALLBACK_RESPONSE =
            "No he podido generar una respuesta ahora mismo. Puedes repetir la pregunta más adelante."
    }
}
