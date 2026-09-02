package dev.pgm.roadmate.utils

/**
 * Recognises the driver controlling how RoadMate *speaks* rather than asking
 * a question: "repite", "más despacio", "más rápido", "voz normal". Checked
 * before every other parser so "repite" is never mistaken for a fresh query.
 */
object PlaybackCommandParser {

    enum class Command { REPEAT, SLOWER, FASTER, NORMAL_SPEED }

    private val REPEAT = spanishRegex(
        """\b(?:rep[ií]te(?:lo|melo)?|repetir|otra\s+vez|de\s+nuevo|""" +
            """vuelve\s+a\s+dec[ií]r(?:melo|lo)?|qu[eé]\s+has\s+dicho|c[oó]mo\s+dices|""" +
            """no\s+te\s+(?:he\s+o[ií]do|entend[ií]|escuch[eé])|no\s+lo\s+he\s+pillado)\b""",
    )

    private val SLOWER = spanishRegex(
        """\b(?:m[aá]s\s+(?:despacio|lento|lenta)|habla\s+m[aá]s\s+despacio|despacito|""" +
            """(?:vas|hablas)\s+muy\s+r[aá]pido)\b""",
    )

    private val FASTER = spanishRegex(
        """\b(?:m[aá]s\s+(?:r[aá]pido|r[aá]pida|deprisa|dep[rl]isa)|habla\s+m[aá]s\s+r[aá]pido|""" +
            """(?:vas|hablas)\s+muy\s+(?:lento|despacio))\b""",
    )

    private val NORMAL_SPEED = spanishRegex(
        """\b(?:voz\s+normal|velocidad\s+normal|a\s+velocidad\s+normal|""" +
            """(?:habla|voz)\s+(?:a\s+)?ritmo\s+normal|normal\s+la\s+voz)\b""",
    )

    fun parse(userInput: String): Command? {
        val text = userInput.trim()
        return when {
            NORMAL_SPEED.containsMatchIn(text) -> Command.NORMAL_SPEED
            SLOWER.containsMatchIn(text) -> Command.SLOWER
            FASTER.containsMatchIn(text) -> Command.FASTER
            REPEAT.containsMatchIn(text) -> Command.REPEAT
            else -> null
        }
    }
}
