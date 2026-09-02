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
     * Needs stated as needs, not as searches.
     *
     * A driver saying "tengo hambre" or "necesito echar gasolina" is asking
     * for the same thing as "busca restaurantes" — they just aren't phrasing
     * it as a search, and it used to go to the model, which would sympathise
     * instead of showing anything. Each entry rewrites the utterance to the
     * plain category query the rest of the pipeline already understands, so
     * nothing downstream has to learn about this.
     *
     * Deliberately whole phrases, not keywords: "gasolina" on its own also
     * appears in "cuánta gasolina me queda", which is a question for the
     * model, not a request for a map.
     */
    private val IMPLICIT_NEEDS: List<Pair<Regex, String>> = listOf(
        spanishRegex(
            """(?:tengo|me\s+est[aá]\s+entrando)\s+(?:mucha\s+)?hambre|""" +
                """quiero\s+comer(?:\s+algo)?|""" +
                """(?:necesito|hay\s+que)\s+parar\s+a\s+comer""",
        ) to "restaurantes",
        spanishRegex(
            """(?:necesito|tengo\s+que|hay\s+que|quiero)\s+(?:echar|poner|repostar)(?:\s+gasolina)?|""" +
                """me\s+estoy\s+quedando\s+sin\s+(?:gasolina|combustible)|""" +
                """(?:me\s+)?queda\s+poca\s+gasolina|""" +
                """(?:necesito|quiero)\s+(?:gasolina|combustible|repostar)""",
        ) to "gasolineras",
        spanishRegex(
            """(?:necesito|quiero|tengo\s+que)\s+(?:parar\s+a\s+)?dormir|""" +
                """(?:necesito|quiero|busco)\s+(?:un\s+)?(?:hotel|sitio\s+donde\s+dormir|d[oó]nde\s+dormir)|""" +
                """(?:necesito|quiero)\s+parar\s+a\s+descansar""",
        ) to "hoteles",
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

        // Last, so an explicit search always wins over an inferred need.
        IMPLICIT_NEEDS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(trimmedInput) }
            ?.let { return it.second }

        return null
    }

    private fun clean(raw: String): String =
        raw.trim()
            .trimEnd('.', '?', '!', ' ')
            .replace(TRAILING_FILLER, "")
            .trim()
}
