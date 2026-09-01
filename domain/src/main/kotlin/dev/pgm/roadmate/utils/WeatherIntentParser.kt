package dev.pgm.roadmate.utils

/**
 * Recognizes the driver asking about the weather: "¿qué tiempo hace?",
 * "¿va a llover?", "¿cuántos grados hay?", "¿cómo está el cielo?"… Checked
 * before Gemini in GenerateResponseUseCase — same shortcut pattern as
 * [StylePreferenceParser] — so the answer comes straight from the fetched
 * weather instead of the model having to infer it (a 0.5B model often
 * doesn't, and in "modo básico" there's no model at all).
 *
 * "tiempo" alone is ambiguous in Spanish (also "duration"), so a bare
 * "tiempo" only counts when it's clearly the weather sense
 * ("qué tiempo", "el tiempo hoy", …) and never when it's "cuánto tiempo",
 * "a tiempo" and the like.
 */
object WeatherIntentParser {

    private val WEATHER_WORDS = Regex(
        """\b(?:llover|lloviendo|llover[aá]|lluvia|chispea|nublad[oa]s?|despejad[oa]s?|""" +
            """solead[oa]|nieve|nevando|granizo|niebla|temperatura|grados|pron[oó]stico|""" +
            """clima|bochorno|hela(?:da|r)|viento|ventoso)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val WEATHER_TIEMPO = Regex(
        """(?:qu[eé]\s+tiempo|c[oó]mo\s+est[aá]\s+el\s+(?:tiempo|cielo|d[ií]a)|""" +
            """el\s+tiempo\s+(?:hoy|ahora|para\s+hoy|de\s+hoy|que\s+hace|que\s+va)|""" +
            """hace\s+(?:frío|frio|calor|sol|bueno|mal\s+tiempo|buen\s+tiempo))""",
        RegexOption.IGNORE_CASE,
    )

    /** "cuánto tiempo", "a tiempo", "tiempo libre"… — duration, not weather. */
    private val NOT_WEATHER_TIEMPO = Regex(
        """\b(?:cu[aá]nto\s+tiempo|a\s+tiempo|tiempo\s+(?:libre|de\s+llegada|restante|que\s+queda|para\s+llegar))\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isWeatherQuestion(userInput: String): Boolean {
        val text = userInput.trim()
        if (NOT_WEATHER_TIEMPO.containsMatchIn(text)) return false
        return WEATHER_WORDS.containsMatchIn(text) || WEATHER_TIEMPO.containsMatchIn(text)
    }
}
