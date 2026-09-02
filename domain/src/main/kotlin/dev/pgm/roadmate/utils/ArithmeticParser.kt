package dev.pgm.roadmate.utils

/**
 * "¿cuánto es quince por cuatro?" → "15 por 4 son 60." Handled as a shortcut
 * (before the model) because a small on-device model gets arithmetic wrong
 * — it answered "quince por cuatro es treinta y cinco" on device.
 *
 * Operands are digits or Spanish number words ([SpanishNumbers]); the four
 * operations go by their spoken names. Anything outside that shape returns
 * null and falls through to the model.
 */
object ArithmeticParser {

    private val PATTERN = spanishRegex(
        """^(?:cu[aá]nto\s+(?:es|son)|cu[aá]nto\s+da|calcula)\s+""" +
            """(.+?)\s+(por|x|multiplicado\s+por|entre|dividido\s+(?:por|entre)|m[aá]s|menos)\s+""" +
            """(.+?)\s*$""",
    )

    fun evaluate(userInput: String): String? {
        val cleaned = userInput.trim().trim('¿', '?', '¡', '!', '.', ' ')
        val m = PATTERN.find(cleaned) ?: return null
        val a = SpanishNumbers.parse(m.groupValues[1]) ?: return null
        val b = SpanishNumbers.parse(m.groupValues[3]) ?: return null
        val op = m.groupValues[2].lowercase()

        val (symbol, result) = when {
            op == "por" || op == "x" || op.startsWith("multiplic") -> "por" to (a * b)
            op.startsWith("entre") || op.startsWith("dividido") -> {
                if (b == 0.0) return "No se puede dividir entre cero."
                "entre" to (a / b)
            }
            op == "más" || op == "mas" -> "más" to (a + b)
            op == "menos" -> "menos" to (a - b)
            else -> return null
        }
        return "${SpanishNumbers.spoken(a)} $symbol ${SpanishNumbers.spoken(b)} son ${SpanishNumbers.spoken(result)}."
    }
}
