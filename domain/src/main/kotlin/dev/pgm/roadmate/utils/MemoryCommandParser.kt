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

        /** Say back what's remembered. */
        data object Recall : Command
    }

    // "que" / "lo de" is required so "¿te recuerda a algo?" doesn't match.
    private val REMEMBER = Regex(
        """\b(?:recuerda|recu[eé]rdame|apunta|an[oó]ta|ten\s+en\s+cuenta)\s+(?:que|lo\s+de)\s+(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val PREFER = Regex("""\bprefiero\s+(.+)""", RegexOption.IGNORE_CASE)
    private val FORGET = Regex(
        """\b(?:olvida|olv[ií]date)\s+(?:lo\s+de\s+|que\s+|de\s+)?(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val RECALL = Regex(
        """\bqu[eé]\s+(?:sabes|recuerdas|has\s+aprendido|tienes\s+apuntado)\s+(?:de\s+m[ií]|sobre\s+m[ií])?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(userInput: String): Command? {
        val text = userInput.trim().trimEnd('.', '?', '!', ' ')

        RECALL.find(text)?.let { return Command.Recall }
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
