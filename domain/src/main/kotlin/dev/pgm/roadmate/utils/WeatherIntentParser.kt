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

    private val WEATHER_WORDS = spanishRegex(
        """\b(?:llover|lloviendo|llover[aá]|lluvia|chispea|nublad[oa]s?|despejad[oa]s?|""" +
            """solead[oa]|nieve|nevando|granizo|niebla|temperatura|grados|pron[oó]stico|""" +
            """clima|bochorno|hela(?:da|r)|viento|ventoso)\b""",
    )

    private val WEATHER_TIEMPO = spanishRegex(
        """(?:qu[eé]\s+tiempo|c[oó]mo\s+est[aá]\s+el\s+(?:tiempo|cielo|d[ií]a)|""" +
            """el\s+tiempo\s+(?:hoy|ahora|para\s+hoy|de\s+hoy|que\s+hace|que\s+va|en\s+\p{L})|""" +
            """hace\s+(?:frío|frio|calor|sol|bueno|mal\s+tiempo|buen\s+tiempo))""",
    )

    /** "cuánto tiempo", "a tiempo", "tiempo libre"… — duration, not weather. */
    private val NOT_WEATHER_TIEMPO = spanishRegex(
        """\b(?:cu[aá]nto\s+tiempo|a\s+tiempo|tiempo\s+(?:libre|de\s+llegada|restante|que\s+queda|para\s+llegar))\b""",
    )

    fun isWeatherQuestion(userInput: String): Boolean {
        val text = userInput.trim()
        if (NOT_WEATHER_TIEMPO.containsMatchIn(text)) return false
        return WEATHER_WORDS.containsMatchIn(text) || WEATHER_TIEMPO.containsMatchIn(text)
    }

    /** Trailing "… en <sitio>" of a weather question — a leading article dropped. */
    private val PLACE_AFTER_EN = spanishRegex(
        """\ben\s+(?:el\s+|la\s+|los\s+|las\s+)?([\p{L}][\p{L}\s.'’-]{1,39}?)\s*[?!.]*\s*$""",
    )

    /** "en un rato", "en 10 minutos", "en casa", "en general"… — not a place. */
    private val NOT_A_PLACE = spanishRegex(
        """^(?:un|una|unos|unas)\s+(?:rato|ratito|hora|momento|minuto|poco)s?$""" +
            """|^(?:casa|el\s+trabajo|camino|ruta|general|realidad|verdad|serio|breve|directo)$""",
    )

    /**
     * The place a weather question is about ("¿qué tiempo hace en Ronda?" ->
     * "Ronda"), or null when it's about here. Only meaningful once
     * [isWeatherQuestion] is true.
     */
    fun placeIn(userInput: String): String? {
        val match = PLACE_AFTER_EN.find(userInput.trim()) ?: return null
        val place = match.groupValues[1].trim().trim('.', ',', ';', '·').trim()
        if (place.length < 2 || place.any { it.isDigit() }) return null
        if (NOT_A_PLACE.containsMatchIn(place)) return null
        return place.replaceFirstChar { it.uppercaseChar() }
    }
}
