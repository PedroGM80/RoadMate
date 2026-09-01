package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.TravelContext

/**
 * Assembles the text prompt sent to Gemini Nano from the current
 * [TravelContext], the driver's [AnswerStyle] preference, and any
 * [recentExchanges] from on-device memory. Pure Kotlin, no Android
 * dependency — safe to unit test directly.
 */
object PromptBuilder {

    private const val MAX_EXCHANGES = 3

    fun buildPrompt(
        context: TravelContext,
        userInput: String,
        style: AnswerStyle = AnswerStyle.DEFAULT,
        recentExchanges: List<Exchange> = emptyList(),
        driverPreferences: List<String> = emptyList(),
        frequentPlaces: List<String> = emptyList(),
        home: String? = null,
        work: String? = null,
    ): String {
        val location = context.currentLocation
            ?.let { "${it.first}, ${it.second}" }
            ?: "desconocida"
        val destination = context.destination ?: "sin destino definido"
        val hour = "%02d:00".format(context.hour)

        val prompt = buildString {
            appendLine(Constants.GEMINI_SYSTEM_PROMPT)
            appendLine(
                "Usuario está en [$location], va a [$destination], son las [$hour]. " +
                    "Pregunta: [$userInput]. ${style.promptInstruction}"
            )

            if (!context.weatherDescription.isNullOrBlank()) {
                appendLine("Clima actual: ${context.weatherDescription}")
            }

            if (driverPreferences.isNotEmpty()) {
                appendLine("Lo que sabes del conductor (tenlo en cuenta):")
                driverPreferences.forEach { appendLine("- $it") }
            }

            if (frequentPlaces.isNotEmpty()) {
                appendLine("Sitios a los que suele ir: ${frequentPlaces.joinToString(", ")}.")
            }

            if (home != null) appendLine("Casa del conductor (lat,lon): [$home]")
            if (work != null) appendLine("Trabajo del conductor (lat,lon): [$work]")

            if (recentExchanges.isNotEmpty()) {
                appendLine("Antes en esta conversación (para dar continuidad):")
                recentExchanges.takeLast(MAX_EXCHANGES).forEach {
                    appendLine("- Él/ella: ${it.question}")
                    appendLine("  Tú respondiste: ${it.answer}")
                }
            }
        }.trim()

        return prompt.take(Constants.MAX_CONTEXT_LENGTH)
    }
}
