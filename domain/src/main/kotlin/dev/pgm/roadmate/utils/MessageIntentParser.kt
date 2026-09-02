package dev.pgm.roadmate.utils

/**
 * "manda un mensaje a Ana: llego en 20", "dile a Juan que voy de camino",
 * "escríbele a mamá diciendo que llego tarde" — a text to send by voice.
 * [recipient] is looked up in contacts; [body] is sent verbatim.
 */
object MessageIntentParser {

    data class Request(val recipient: String, val body: String)

    private val PATTERNS = listOf(
        spanishRegex(
            """(?:manda|env[ií]a|m[aá]ndale|escr[ií]bele|escribe|pon(?:le)?)\s+""" +
                """(?:un\s+|una\s+)?(?:mensaje|sms|wasap|whatsapp|nota)?\s*(?:a|para)\s+""" +
                """(.+?)\s*(?::|,|\s+que\s+|\s+dici[eé]ndo(?:le)?\s+(?:que\s+)?|\s+con\s+el\s+texto\s+)\s*(.+)""",
        ),
        spanishRegex("""d[ií]le\s+a\s+(.+?)\s+que\s+(.+)"""),
        spanishRegex("""av[ií]sale?\s+a\s+(.+?)\s+(?:de\s+)?que\s+(.+)"""),
    )

    fun parse(userInput: String): Request? {
        val text = userInput.trim().trim('¿', '?', '¡', '!', '.', ' ')
        for (pattern in PATTERNS) {
            val m = pattern.find(text) ?: continue
            val recipient = m.groupValues[1].trim().trim('.', ',').trim()
            val body = m.groupValues[2].trim().trim('.', ' ').trim()
            if (recipient.length >= 2 && body.isNotBlank()) {
                return Request(recipient.replaceFirstChar { it.uppercaseChar() }, body)
            }
        }
        return null
    }
}
