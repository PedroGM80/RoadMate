package dev.pgm.roadmate.utils

/**
 * Recognizes "busca gasolineras", "encuentra un hotel cerca", "dónde hay un
 * restaurante", "llévame a la playa", "cómo llego al hospital" and similar —
 * anything asking to find or navigate to a place, not to look up a fact.
 * The extracted text is handed straight to the device's Maps app as a search
 * query, so it deliberately stays free-text rather than a fixed POI category
 * list: whatever the driver says is exactly what gets searched.
 */
object MapSearchIntentParser {
    /**
     * "Take me there" phrasings. These always name a destination, so they skip
     * the [NOT_A_PLACE] guard (which exists to stop "busca cómo…" fact
     * lookups — it would otherwise also swallow "cómo llego a…").
     */
    private val NAVIGATE_PATTERNS = listOf(
        spanishRegex(
            """(?:ll[eé]vame|llevame|gu[ií]ame|guiame|c[oó]mo\s+(?:llego|voy|se\s+va|ir))\s+""" +
                """(?:a|al|hasta|hacia|para)\s+(.+)""",
        ),
    )

    private val FIND_PATTERNS = listOf(
        spanishRegex("""(?:busca|buscar|busco|encuentra|encontrar)\s+(.+)"""),
        spanishRegex("""(?:dónde|donde)\s+hay\s+(.+)"""),
        spanishRegex("""(?:dónde|donde)\s+(?:está|esta|queda)\s+(.+)"""),
        spanishRegex("""hay\s+(?:alguna?|algún|algun)\s+(.+)"""),
    )

    private val TRAILING_FILLER = spanishRegex(
        """\s+(?:""" +
            """cerca(?:\s+de\s+(?:aqu[ií]|m[ií]))?|""" +
            """m[aá]s\s+cercan[oa]s?|cercan[oa]s?|""" +
            """(?:por\s+)?aqu[ií](?:\s+cerca)?|(?:por|en)\s+la\s+zona""" +
            """)\s*$""",
    )

    /** "busca información sobre…", "busca en internet…" — a fact lookup, not a place. */
    private val NOT_A_PLACE = spanishRegex(
        """^(?:informaci[oó]n|datos?|la\s+respuesta|c[oó]mo|qu[eé]|qui[eé]n|cu[aá]l|cu[aá]ndo|cu[aá]nto|""" +
            """por\s+qu[eé]|significa|el\s+significado|en\s+(?:internet|la\s+web|google))\b""",
    )

    /**
     * True when the phrasing asks to *go* somewhere ("llévame a…", "cómo
     * llego a…") rather than only find it — the caller then draws a route,
     * not just a pin.
     */
    fun isNavigationRequest(userInput: String): Boolean {
        val trimmed = userInput.trim()
        return NAVIGATE_PATTERNS.any { p ->
            p.find(trimmed)?.let { clean(it.groupValues[1]).isNotBlank() } == true
        }
    }

    fun extractSearchQuery(userInput: String): String? {
        val trimmedInput = userInput.trim()

        for (pattern in NAVIGATE_PATTERNS) {
            val query = pattern.find(trimmedInput)?.let { clean(it.groupValues[1]) }
            if (!query.isNullOrBlank()) return query
        }

        for (pattern in FIND_PATTERNS) {
            val query = pattern.find(trimmedInput)?.let { clean(it.groupValues[1]) } ?: continue
            if (query.isNotBlank() && !NOT_A_PLACE.containsMatchIn(query)) return query
        }
        return null
    }

    private fun clean(raw: String): String =
        raw.trim()
            .trimEnd('.', '?', '!', ' ')
            .replace(TRAILING_FILLER, "")
            .trim()
}
