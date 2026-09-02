package dev.pgm.roadmate.utils

/**
 * Recognizes the driver explicitly managing what RoadMate remembers:
 * "recuerda que no me gustan las autovías", "prefiero las nacionales",
 * "olvida lo de las autovías", "¿qué sabes de mí?". Checked before Gemini in
 * GenerateResponseUseCase — a match is memory management, not a question.
 */
object MemoryCommandParser {

    sealed interface Command {
        /** Store [value] as a preference, verbatim. */
        data class Remember(val value: String) : Command

        /** Drop preferences matching [match] (or all, if blank). */
        data class Forget(val match: String) : Command

        /** Say back what's remembered (preferences). */
        data object Recall : Command

        /** Look up what was said earlier about [term]. */
        data class Search(val term: String) : Command

        /** Save the current location as home / work. */
        data object SetHome : Command
        data object SetWork : Command

        /** "[name] es mi [relation]" — [relation] is lowercased. */
        data class SetRelationship(val relation: String, val name: String) : Command
    }

    /** Relationship words RoadMate understands after "mi …". */
    val RELATIONS = setOf(
        "madre", "padre", "mama", "mamá", "papa", "papá",
        "hermano", "hermana", "hijo", "hija",
        "mujer", "marido", "esposo", "esposa", "pareja", "novio", "novia",
        "jefe", "jefa", "abuelo", "abuela", "tio", "tío", "tia", "tía",
        "primo", "prima", "suegro", "suegra", "cuñado", "cuñada", "amigo", "amiga",
    )

    private val relationAlt = RELATIONS.joinToString("|")
    private val SET_HOME = spanishRegex(
        """\b(?:esta\s+es\s+mi\s+casa|guarda\s+(?:esto|esta\s+ubicaci[oó]n)\s+como\s+(?:mi\s+)?casa|aqu[ií]\s+vivo)\b""",
    )
    private val SET_WORK = spanishRegex(
        """\b(?:aqu[ií]\s+(?:es|est[aá])\s+(?:mi\s+)?trabajo|esto\s+es\s+(?:mi\s+)?trabajo|guarda\s+(?:esto|esta\s+ubicaci[oó]n)\s+como\s+(?:el\s+|mi\s+)?trabajo|aqu[ií]\s+trabajo)\b""",
    )
    private val SET_RELATIONSHIP = spanishRegex(
        """(?:\bguarda\s+a\s+(?<n1>.+?)\s+como\s+mi\s+(?<r1>$relationAlt)\b)""" +
            """|(?:^(?<n2>.+?)\s+es\s+mi\s+(?<r2>$relationAlt)\b)""",
    )

    // "que" / "lo de" is required so "¿te recuerda a algo?" doesn't match.
    private val REMEMBER = spanishRegex(
        """\b(?:recuerda|recu[eé]rdame|apunta|an[oó]ta|ten\s+en\s+cuenta)\s+(?:que|lo\s+de)\s+(.+)""",
    )
    private val PREFER = spanishRegex("""\bprefiero\s+(.+)""")
    private val FORGET = spanishRegex(
        """\b(?:olvida|olv[ií]date)\s+(?:lo\s+de\s+|que\s+|de\s+)?(.+)""",
    )
    private val RECALL = spanishRegex(
        """\bqu[eé]\s+(?:sabes|recuerdas|has\s+aprendido|tienes\s+apuntado)\s+(?:de\s+m[ií]|sobre\s+m[ií])?\s*$""",
    )
    private val SEARCH = spanishRegex(
        """\b(?:qu[eé]\s+(?:te\s+dije|dijiste|dijimos|hablamos|comentamos|coment[eé])|de\s+qu[eé]\s+hablamos)\s+(?:sobre|de|acerca\s+de|del|de\s+la|de\s+lo\s+de)\s+(.+)""",
    )

    fun parse(userInput: String): Command? {
        val text = userInput.trim().trimEnd('.', '?', '!', ' ')

        SEARCH.find(text)?.let { m ->
            val term = m.groupValues[1].trim()
            if (term.isNotBlank()) return Command.Search(term)
        }
        RECALL.find(text)?.let { return Command.Recall }
        if (SET_HOME.containsMatchIn(text)) return Command.SetHome
        if (SET_WORK.containsMatchIn(text)) return Command.SetWork
        SET_RELATIONSHIP.find(text)?.let { m ->
            val name = (m.groups["n1"] ?: m.groups["n2"])?.value?.trim()
            val relation = (m.groups["r1"] ?: m.groups["r2"])?.value?.lowercase()
            if (!name.isNullOrBlank() && relation != null) {
                return Command.SetRelationship(relation, name)
            }
        }
        FORGET.find(text)?.let { return Command.Forget(it.groupValues[1].trim()) }
        REMEMBER.find(text)?.let { m ->
            val v = m.groupValues[1].trim()
            if (v.isNotBlank()) return Command.Remember(v)
        }
        PREFER.find(text)?.let { m ->
            val v = m.groupValues[1].trim()
            if (v.isNotBlank()) return Command.Remember("prefiere $v")
        }
        return null
    }
}
