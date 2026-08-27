package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.TravelContext

/**
 * Assembles the text prompt sent to Gemini Nano from the current [TravelContext].
 * Pure Kotlin, no Android dependency — safe to unit test directly.
 */
object PromptBuilder {

    private const val MAX_LAST_RESPONSES = 3

    fun buildPrompt(context: TravelContext, userInput: String): String {
        val location = context.currentLocation
            ?.let { "${it.first}, ${it.second}" }
            ?: "desconocida"
        val destination = context.destination ?: "sin destino definido"
        val hour = "%02d:00".format(context.hour)

        val prompt = buildString {
            appendLine(Constants.GEMINI_SYSTEM_PROMPT)
            appendLine(
                "Usuario está en [$location], va a [$destination], son las [$hour]. " +
                    "Pregunta: [$userInput]. Responde en 1-2 frases."
            )

            if (context.lastResponses.isNotEmpty()) {
                appendLine("Respuestas anteriores en este viaje (para continuidad):")
                context.lastResponses.takeLast(MAX_LAST_RESPONSES).forEach { appendLine("- $it") }
            }
        }.trim()

        return prompt.take(Constants.MAX_CONTEXT_LENGTH)
    }
}
