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
 *
 * The coordinate lines (own position, home, work) and the weather line are
 * emitted only when the question looks spatial or weather-related — on a
 * ~6 tok/s CPU backend every skipped line shaves prefill off the time to the
 * first spoken word, and they add nothing to an identity or general-knowledge
 * answer. The keyword match errs towards inclusion.
 */
object PromptBuilder {

    private const val MAX_EXCHANGES = 1
    private const val MAX_FIELD_CHARS = 200
    private const val MAX_EXCHANGE_CHARS = 160

    /**
     * Coordinates (own position, home, work) and the weather line are only
     * useful when the question is spatial or weather-related. For identity,
     * general-knowledge and chit-chat questions they're ~30 tokens of prefill
     * on the answer's critical path that change nothing — at ~6 tok/s on this
     * CPU that's real latency before the first spoken word. So each is included
     * only when the question looks like it needs it. The bias is to include:
     * a slightly longer prompt costs a fraction of a second, a missing fact
     * costs a wrong answer.
     */
    private val LOCATION_QUESTION = Regex(
        """\b(?:d[oó]nde|cerca|cercan[oa]s?|lejos|distancia|kil[oó]metros?|km|""" +
            """llego|llegar|llegamos|llegar[eé]|queda|quedan|quedamos|falta|faltan|""" +
            """ruta|camino|trayecto|aqu[ií]|estoy|estamos|ubicaci[oó]n|alrededor|""" +
            """zona|salida|autov[ií]a|autopista|casa|trabajo|barrio)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val WEATHER_QUESTION = Regex(
        """\b(?:tiempo|clima|llover|lloviendo|llover[aá]|lluvia|chispea|nublad[oa]s?|""" +
            """despejad[oa]s?|solead[oa]|nieve|nevando|granizo|niebla|temperatura|""" +
            """grados|pron[oó]stico|fr[ií]o|calor|viento|ventoso|paraguas|chubasquero|""" +
            """abrigo|chaqueta|cadenas)\b""",
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

        val wantsLocation = LOCATION_QUESTION.containsMatchIn(userInput)
        val wantsWeather = WEATHER_QUESTION.containsMatchIn(userInput)

        val prompt = buildString {
            appendLine(Constants.GEMINI_SYSTEM_PROMPT)
            appendLine(style.promptInstruction)
            appendLine()

            appendLine("Contexto:")
            appendLine("- Hora: $time")
            if (wantsLocation) {
                appendLine("- Ubicación (lat,lon): $location")
            }
            context.destination?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Destino: $it") }
            if (wantsWeather) {
                context.weatherDescription?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("- Clima: $it") }
            }
            if (wantsLocation) {
                home?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("- Casa (lat,lon): $it") }
                work?.oneLine(MAX_FIELD_CHARS)?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("- Trabajo (lat,lon): $it") }
            }

            driverPreferences.map { it.oneLine(MAX_FIELD_CHARS) }.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.let { appendLine("- Sabes del conductor: ${it.joinToString("; ")}") }
            frequentPlaces.map { it.oneLine(MAX_FIELD_CHARS) }.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.let { appendLine("- Suele ir a: ${it.joinToString(", ")}") }

            val previous = recentExchanges.takeLast(MAX_EXCHANGES)
                .firstOrNull { it.question.isNotBlank() || it.answer.isNotBlank() }
            if (previous != null) {
                appendLine()
                appendLine("Turno anterior (solo contexto por si la pregunta lo continúa):")
                appendLine("- Conductor: ${previous.question.oneLine(MAX_EXCHANGE_CHARS)}")
                appendLine("- Tú: ${previous.answer.oneLine(MAX_EXCHANGE_CHARS)}")
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
