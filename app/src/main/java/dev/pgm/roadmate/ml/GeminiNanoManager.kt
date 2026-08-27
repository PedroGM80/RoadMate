package dev.pgm.roadmate.ml

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig

/**
 * Sends prompts to the on-device Gemini Nano model via Android AICore and
 * returns the generated text.
 *
 * IMPORTANT: this depends on `com.google.ai.edge.aicore:aicore`, which is not
 * yet declared in gradle/libs.versions.toml — add it before this file will
 * compile. ML Kit's GenAI APIs (Summarization/Rewriting/Proofreading) do not
 * expose free-form prompting, so this targets the AICore SDK instead, which
 * is the actual on-device Gemini Nano entry point. That SDK is still
 * evolving — verify the package/class names below against the AICore
 * version you add, and confirm target devices support AICore before relying
 * on this in production.
 */
class GeminiNanoManager(context: Context) {

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            generationConfig = generationConfig {
                this.context = context.applicationContext
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 256
            }
        )
    }

    suspend fun generateResponse(prompt: String): Result<String> = runCatching {
        val response = model.generateContent(prompt)
        response.text ?: throw IllegalStateException("Gemini Nano returned an empty response")
    }
}
