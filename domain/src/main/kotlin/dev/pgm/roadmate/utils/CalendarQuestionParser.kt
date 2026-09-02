package dev.pgm.roadmate.utils

/**
 * "¿qué tengo hoy?", "¿mi agenda de hoy?", "¿cuál es mi próxima cita?",
 * "¿qué planes tengo esta tarde?".
 */
object CalendarQuestionParser {

    enum class Scope { TODAY, NEXT }

    private val NEXT = spanishRegex(
        """(?:pr[oó]xim[ao]|siguiente)\s+(?:cita|evento|reuni[oó]n|compromiso)|""" +
            """qu[eé]\s+(?:tengo|hay)\s+(?:ahora|luego|despu[eé]s)|a\s+qu[eé]\s+hora\s+es\s+(?:mi\s+)?(?:cita|reuni[oó]n)""",
    )

    private val TODAY = spanishRegex(
        """qu[eé]\s+(?:tengo|hay|planes\s+tengo)\s+(?:hoy|para\s+hoy|esta\s+(?:ma[ñn]ana|tarde|noche)|el\s+d[ií]a)|""" +
            """mi\s+agenda(?:\s+de\s+hoy)?|(?:la\s+)?agenda\s+de\s+hoy|qu[eé]\s+hay\s+en\s+(?:mi\s+)?agenda|""" +
            """tengo\s+algo\s+(?:hoy|esta\s+(?:tarde|ma[ñn]ana|noche))""",
    )

    fun parse(userInput: String): Scope? {
        val t = userInput.trim()
        return when {
            NEXT.containsMatchIn(t) -> Scope.NEXT
            TODAY.containsMatchIn(t) -> Scope.TODAY
            else -> null
        }
    }
}
