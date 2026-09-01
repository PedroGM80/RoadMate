package dev.pgm.roadmate.utils

/**
 * Recognizes "busca gasolineras", "encuentra un hotel cerca", "dónde hay un
 * restaurante" and similar — anything asking to find a place, not a fact.
 * The extracted text is handed straight to the device's Maps app as a search
 * query, so it deliberately stays free-text rather than a fixed POI category
 * list: whatever the driver says is exactly what gets searched.
 */
object MapSearchIntentParser {
    private val PATTERNS = listOf(
        Regex("""(?:busca|buscar|encuentra|encontrar)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:dónde|donde)\s+hay\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:dónde|donde)\s+(?:está|esta)\s+(.+)\s+m[aá]s\s+cercan[oa]""", RegexOption.IGNORE_CASE)
    )

    private val TRAILING_FILLER = Regex(
        """\s+(?:cerca(?:\s+de\s+(?:aqu[ií]|m[ií]))?|m[aá]s\s+cercan[oa]s?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** "busca información sobre…", "busca en internet…" — a fact lookup, not a place. */
    private val NOT_A_PLACE = Regex(
        """^(?:informaci[oó]n|datos?|la\s+respuesta|c[oó]mo|qu[eé]|cu[aá]l|por\s+qu[eé]|en\s+(?:internet|la\s+web|google))\b""",
        RegexOption.IGNORE_CASE,
    )

    fun extractSearchQuery(userInput: String): String? {
        val trimmedInput = userInput.trim()
        for (pattern in PATTERNS) {
            val match = pattern.find(trimmedInput) ?: continue
            val query = match.groupValues[1]
                .trim()
                .trimEnd('.', '?', '!', ' ')
                .replace(TRAILING_FILLER, "")
                .trim()
            if (query.isNotBlank() && !NOT_A_PLACE.containsMatchIn(query)) return query
        }
        return null
    }
}
