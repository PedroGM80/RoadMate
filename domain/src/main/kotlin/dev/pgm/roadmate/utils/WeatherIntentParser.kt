package dev.pgm.roadmate.utils

/**
 * Recognizes the driver asking about the weather: "¿qué tiempo hace?",
 * "¿va a llover?", "¿cuántos grados hay?", "¿cómo está el cielo?",
 * "el tiempo meteorológico en Cádiz"… Checked before Gemini in
 * GenerateResponseUseCase — same shortcut pattern as [StylePreferenceParser] —
 * so the answer comes straight from the fetched weather instead of the model
 * having to infer it (a 0.5B model often reads "tiempo" as *duration* and
 * answers with a travel time; in "modo básico" there's no model at all).
 *
 * "tiempo" alone is ambiguous in Spanish (also "duration"), so a bare
 * "tiempo" only counts when it's clearly the weather sense ("qué tiempo",
 * "el tiempo hoy", "el tiempo en/de <sitio>", "tiempo meteorológico"…) and
 * never when it's "cuánto tiempo", "a tiempo", "el tiempo de espera"…
 */
object WeatherIntentParser {

    private val WEATHER_WORDS = spanishRegex(
        """\b(?:llover|lloviendo|llover[aá]|lluvia|chispea|nublad[oa]s?|despejad[oa]s?|""" +
            """solead[oa]|nieve|nevando|granizo|niebla|temperatura|grados|pron[oó]stico|""" +
            """clima|climatolog[ií]a|meteorol[oó]gic[oa]s?|meteorolog[ií]a|""" +
            """bochorno|hela(?:da|r)|viento|ventoso)\b""",
    )

    private val WEATHER_TIEMPO = spanishRegex(
        """(?:qu[eé]\s+tiempo|c[oó]mo\s+est[aá]\s+el\s+(?:tiempo|cielo|d[ií]a)|""" +
            """el\s+tiempo(?:\s+meteorol[oó]gico)?\s+""" +
            """(?:hoy|ahora|ma[ñn]ana|para\s+hoy|de\s+hoy|que\s+hace|que\s+va|""" +
            """en\s+\p{L}|de\s+\p{L}|para\s+\p{L})|""" +
            """hace\s+(?:frío|frio|calor|sol|bueno|mal\s+tiempo|buen\s+tiempo))""",
    )

    /** "cuánto tiempo", "a tiempo", "tiempo libre", "el tiempo de viaje"… — not weather. */
    private val NOT_WEATHER_TIEMPO = spanishRegex(
        """\b(?:cu[aá]nto\s+tiempo|a\s+tiempo|""" +
            """tiempo\s+(?:libre|restante|que\s+queda|para\s+llegar|""" +
            """de\s+(?:llegada|espera|sobra|m[aá]s|menos|vida|juego|trabajo|descanso|""" +
            """viaje|trayecto|conducci[oó]n|ruta|camino|coche)))\b""",
    )

    fun isWeatherQuestion(userInput: String): Boolean {
        val text = userInput.trim()
        return !NOT_WEATHER_TIEMPO.containsMatchIn(text) && (WEATHER_WORDS.containsMatchIn(text) || WEATHER_TIEMPO.containsMatchIn(text))
    }

    /** Trailing "… en/de/para <sitio>" of a weather question, a leading article dropped. */
    private val PLACE_TAIL = spanishRegex(
        """\b(?:en|de|para)\s+(?:el\s+|la\s+|los\s+|las\s+)?([\p{L}][\p{L}\s.'’-]{1,39}?)\s*[?!.]*\s*$""",
    )

    /**
     * "en un rato", "en 10 minutos", "de hoy", "en mi posición", "aquí"… —
     * a time expression or "right here", not a place to look up elsewhere.
     */
    private val NOT_A_PLACE = spanishRegex(
        """^(?:un|una|unos|unas)\s+(?:rato|ratito|hora|momento|minuto|poco)s?$""" +
            """|^(?:hoy|ahora|ma[ñn]ana|ayer|luego|antes|despu[eé]s|siempre|un\s+rato)$""" +
            """|^mi\s+(?:posici[oó]n|ubicaci[oó]n|zona|sitio|ciudad|pueblo|casa|barrio|tierra)$""" +
            """|^(?:aqu[ií]|ac[aá]|casa|el\s+trabajo|camino|ruta|general|realidad|verdad|serio|""" +
            """breve|directo|la\s+zona|esta\s+zona|donde\s+estoy|mi\s+casa)$""",
    )

    /**
     * The place a weather question is about ("¿qué tiempo hace en Ronda?" ->
     * "Ronda", "el tiempo de Cádiz" -> "Cádiz"), or null when it's about here.
     * Only meaningful once [isWeatherQuestion] is true.
     */
    fun placeIn(userInput: String): String? {
        val match = PLACE_TAIL.find(userInput.trim()) ?: return null
        val place = match.groupValues[1].trim().trim('.', ',', ';', '·').trim()
        if (place.length < 2 || place.any { it.isDigit() }) return null
        if (NOT_A_PLACE.containsMatchIn(place)) return null
        return place.replaceFirstChar { it.uppercaseChar() }
    }
}
