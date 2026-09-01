package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.TravelContext
import java.util.Locale

/**
 * Assembles the text prompt for the local model from the current
 * [TravelContext], the driver's [AnswerStyle] preference, and any
 * [recentExchanges] from on-device memory. Pure Kotlin, no Android
 * dependency — safe to unit test directly.
 *
 * Shape matters for small on-device models: the instruction comes first,
 * context in the middle as short labelled lines, and the driver's question
 * last immediately before an explicit "Respuesta:" cue — a weak model given
 * the question mid-blob tends to rephrase it instead of answering. Every
 * interpolated value is flattened to a single line and length-capped so a
 * stray long string can't blow past the model's context or crash the
 * native tokenizer.
 */
object PromptBuilder {

    private const val MAX_EXCHANGES = 2
    private const val MAX_FIELD_CHARS = 200
    private const val MAX_EXCHANGE_CHARS = 160

    /**
     * Only worth spending prompt budget on past turns when the question can't
     * stand alone. A small model given unrelated history tends to answer the
     * old question instead of the new one, so a self-contained question like
     * "¿cuál es la capital de Francia?" gets no history at all.
     */
    private val FOLLOW_UP = Regex(
        """\b(?:eso|esa|ese|esos|esas|est[oae]s?|aquell[oa]s?|ah[ií]|all[ií]|""" +
            """s[ií]|no|vale|y\s|entonces|tambi[eé]n|otra|otro|m[aá]s|repite|repítelo|""" +
            """por\s+qu[eé]|cu[aá]l\s+de|el\s+primero|el\s+segundo|la\s+primera|la\s+segunda)\b""",
        RegexOption.IGNORE_CASE,
    )

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
            ?.let { (lat, lon) -> "%.4f, %.4f".format(Locale.US, lat, lon) }
            ?: "desconocida"
        val time = "%02d:%02d".format(Locale.US, context.hour, context.minute)
        val question = userInput.oneLine(MAX_FIELD_CHARS).ifBlank { "(sin pregunta)" }

        val prompt = buildString {
            appendLine(Constants.GEMINI_SYSTEM_PROMPT)
            appendLine(style.promptInstruction)
            appendLine()

            appendLine("Contexto:")
            appendLine("- Hora: $time")
            appendLine("- Ubicación (lat,lon): $location")
            context.destination?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Destino: $it") }
            context.weatherDescription?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Clima: $it") }
            home?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Casa (lat,lon): $it") }
            work?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Trabajo (lat,lon): $it") }

            driverPreferences.map { it.oneLine(MAX_FIELD_CHARS) }.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.let { appendLine("- Sabes del conductor: ${it.joinToString("; ")}") }
            frequentPlaces.map { it.oneLine(MAX_FIELD_CHARS) }.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.let { appendLine("- Suele ir a: ${it.joinToString(", ")}") }

            val exchanges = recentExchanges.takeLast(MAX_EXCHANGES)
                .filter { it.question.isNotBlank() || it.answer.isNotBlank() }
            if (exchanges.isNotEmpty() && FOLLOW_UP.containsMatchIn(question)) {
                appendLine()
                appendLine("Conversación reciente (solo como contexto, responde a la última pregunta):")
                exchanges.forEach {
                    appendLine("- Conductor: ${it.question.oneLine(MAX_EXCHANGE_CHARS)}")
                    appendLine("- Tú: ${it.answer.oneLine(MAX_EXCHANGE_CHARS)}")
                }
            }

            appendLine()
            appendLine("Pregunta del conductor: $question")
            append("Respuesta:")
        }

        return prompt.take(Constants.MAX_CONTEXT_LENGTH)
    }

    /** Collapse whitespace/newlines to a single line and hard-cap the length. */
    private fun String.oneLine(max: Int): String =
        replace(Regex("\\s+"), " ").trim().take(max)
}
