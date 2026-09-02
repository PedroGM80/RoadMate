package dev.pgm.roadmate.utils

import kotlin.math.roundToLong

/**
 * Spoken Spanish numbers → Double, and Double → the short spoken form.
 *
 * Vosk transcribes numbers as words ("ciento veinte", "tres mil quinientos"),
 * so every voice shortcut that takes a quantity — arithmetic, unit
 * conversion — needs this. Handles 0–999 999 as words plus plain digits
 * (with "," or "." as the decimal mark).
 */
object SpanishNumbers {

    fun parse(raw: String): Double? {
        val t = raw.trim().lowercase().replace('-', ' ').replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        t.replace(',', '.').toDoubleOrNull()?.let { return it }
        return words(t)?.toDouble()
    }

    /** "60", "3,5" — integers plain, fractions with a comma, at most 2 decimals. */
    fun spoken(n: Double): String {
        if (n == n.roundToLong().toDouble()) return n.roundToLong().toString()
        return "%.2f".format(n).trimEnd('0').trimEnd('.').replace('.', ',')
    }

    private val UNITS: Map<String, Int> = buildMap {
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

    private val HUNDREDS = buildMap {
        put("cien", 100); put("ciento", 100)
        listOf(
            "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos",
            "setecientos", "ochocientos", "novecientos",
        ).forEachIndexed { i, w ->
            put(w, (i + 2) * 100)
            put(w.dropLast(2) + "as", (i + 2) * 100) // doscientas, quinientas…
        }
    }

    private fun words(t: String): Int? {
        if (t == "mil") return 1000
        val milAt = t.indexOf(" mil")
        if (milAt >= 0) {
            val left = t.substring(0, milAt).trim()
            val right = t.substring(milAt + 4).trim().removePrefix("y ").trim()
            val thousands = if (left.isEmpty()) 1 else under1000(left) ?: return null
            val rest = if (right.isEmpty()) 0 else under1000(right) ?: return null
            return thousands * 1000 + rest
        }
        return under1000(t)
    }

    private fun under1000(t: String): Int? {
        under100(t)?.let { return it }
        HUNDREDS[t]?.let { return it }
        for ((word, hundreds) in HUNDREDS) {
            if (t.startsWith("$word ")) {
                val rest = under100(t.removePrefix("$word ").trim()) ?: return null
                return hundreds + rest
            }
        }
        return null
    }

    private fun under100(t: String): Int? {
        UNITS[t]?.let { return it }
        TENS[t]?.let { return it }
        val parts = t.split(Regex("\\s+y\\s+"))
        if (parts.size == 2) {
            val tens = TENS[parts[0]] ?: return null
            val unit = UNITS[parts[1]] ?: return null
            if (unit in 1..9) return tens + unit
        }
        return null
    }
}
