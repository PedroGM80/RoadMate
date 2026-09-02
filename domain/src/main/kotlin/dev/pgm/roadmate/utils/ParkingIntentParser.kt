package dev.pgm.roadmate.utils

/**
 * "he aparcado aquí" / "¿dónde aparqué?" / "llévame al coche" — saving and
 * finding where the car was left.
 */
object ParkingIntentParser {

    enum class Intent { SAVE, WHERE, TAKE_ME }

    private val SAVE = spanishRegex(
        """(?:he\s+)?aparc(?:o|ad?o|u[eé])\s+aqu[ií]|""" +
            """(?:guarda|apunta|recuerda|marca)\s+(?:d[oó]nde\s+)?(?:he\s+)?""" +
            """(?:aparc(?:ad?o|u[eé])|dej(?:ad?o|[eé])\s+el\s+coche)|""" +
            """(?:guarda|marca)\s+(?:el\s+)?(?:aparcamiento|sitio\s+del\s+coche)|""" +
            """aqu[ií]\s+(?:he\s+)?(?:aparc(?:ad?o|u[eé])|dej(?:ad?o|[eé])\s+el\s+coche)""",
    )

    private val TAKE_ME = spanishRegex(
        """(?:ll[eé]vame|gu[ií]ame|volver|vuelve|c[oó]mo\s+(?:llego|vuelvo|voy))\s+(?:de\s+vuelta\s+)?al\s+coche|""" +
            """(?:ll[eé]vame|gu[ií]ame)\s+(?:hasta|a)\s+(?:donde\s+)?aparqu[eé]""",
    )

    private val WHERE = spanishRegex(
        """d[oó]nde\s+(?:he\s+)?(?:aparqu[eé]|aparcad?o|dej[eé]\s+el\s+coche|est[aá]\s+(?:el|mi)\s+coche)|""" +
            """(?:encuentra|busca)\s+(?:el|mi)\s+coche|d[oó]nde\s+dej[eé]\s+aparcad?o""",
    )

    fun parse(userInput: String): Intent? {
        val text = userInput.trim()
        return when {
            TAKE_ME.containsMatchIn(text) -> Intent.TAKE_ME
            SAVE.containsMatchIn(text) -> Intent.SAVE
            WHERE.containsMatchIn(text) -> Intent.WHERE
            else -> null
        }
    }
}
