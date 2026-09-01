package dev.pgm.roadmate.utils

import kotlin.math.roundToLong

/**
 * "¿cuánto es quince por cuatro?" → "15 por 4 son 60." Handled as a shortcut
 * (before the model) because a small on-device model gets arithmetic wrong
 * — it answered "quince por cuatro es treinta y cinco" on device.
 *
 * Covers the two operands as digits or Spanish number words 0–100, and the
 * four operations by their spoken names. Anything outside that shape returns
 * null and falls through to the model.
 */
object ArithmeticParser {

    private val PATTERN = Regex(
        """^(?:cu[aá]nto\s+(?:es|son)|cu[aá]nto\s+da|calcula)\s+""" +
            """(.+?)\s+(por|x|multiplicado\s+por|entre|dividido\s+(?:por|entre)|m[aá]s|menos)\s+""" +
            """(.+?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun evaluate(userInput: String): String? {
        val cleaned = userInput.trim().trim('¿', '?', '¡', '!', '.', ' ')
        val m = PATTERN.find(cleaned) ?: return null
        val a = spanishNumber(m.groupValues[1]) ?: return null
        val b = spanishNumber(m.groupValues[3]) ?: return null
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
        return "${format(a)} $symbol ${format(b)} son ${format(result)}."
    }

    private fun format(n: Double): String {
        if (n == n.roundToLong().toDouble()) return n.roundToLong().toString()
        return "%.2f".format(n).trimEnd('0').trimEnd('.', ',').replace('.', ',')
    }

    private val UNITS = buildMap {
        listOf(
            "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete",
            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidos", "veintitres",
            "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve",
        ).forEachIndexed { i, w -> put(w, i) }
        put("un", 1); put("una", 1)
        put("dieciséis", 16); put("veintiún", 21); put("veintiuna", 21)
        put("veintidós", 22); put("veintitrés", 23); put("veintiséis", 26)
    }
    private val TENS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    )

    private fun spanishNumber(raw: String): Double? {
        val t = raw.trim().lowercase()
        t.toDoubleOrNull()?.let { return it }
        if (t == "cien" || t == "ciento") return 100.0
        UNITS[t]?.let { return it.toDouble() }
        TENS[t]?.let { return it.toDouble() }
        val parts = t.split(Regex("\\s+y\\s+"))
        if (parts.size == 2) {
            val tens = TENS[parts[0]] ?: return null
            val unit = UNITS[parts[1]] ?: return null
            if (unit in 1..9) return (tens + unit).toDouble()
        }
        return null
    }
}
