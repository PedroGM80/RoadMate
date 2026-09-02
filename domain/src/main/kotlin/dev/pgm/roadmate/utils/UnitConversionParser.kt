package dev.pgm.roadmate.utils

/**
 * "¿cuántas millas son 120 km?", "pasa 3 galones a litros", "30 grados a
 * fahrenheit". A driving-relevant unit set only; the number may be digits or
 * Spanish words ([SpanishNumbers]). Anything else returns null → the model.
 */
object UnitConversionParser {

    private data class U(val dim: String, val toBase: Double, val spoken: String)

    // Base units: length = metre, mass = gram, volume = litre, speed = km/h.
    private val CANON = mapOf(
        "km" to U("len", 1000.0, "kilómetros"),
        "m" to U("len", 1.0, "metros"),
        "cm" to U("len", 0.01, "centímetros"),
        "mi" to U("len", 1609.344, "millas"),
        "ft" to U("len", 0.3048, "pies"),
        "yd" to U("len", 0.9144, "yardas"),
        "kg" to U("mass", 1000.0, "kilos"),
        "g" to U("mass", 1.0, "gramos"),
        "lb" to U("mass", 453.592, "libras"),
        "oz" to U("mass", 28.3495, "onzas"),
        "l" to U("vol", 1.0, "litros"),
        "gal" to U("vol", 3.785_41, "galones"),
        "kmh" to U("spd", 1.0, "kilómetros por hora"),
        "mph" to U("spd", 1.609_344, "millas por hora"),
        "c" to U("temp", 0.0, "grados centígrados"),
        "f" to U("temp", 0.0, "grados Fahrenheit"),
    )

    private val ALIAS = buildMap {
        fun add(canon: String, vararg words: String) = words.forEach { put(it, canon) }
        add("km", "km", "kilometro", "kilómetro", "kilometros", "kilómetros")
        add("m", "m", "metro", "metros")
        add("cm", "cm", "centimetro", "centímetro", "centimetros", "centímetros")
        add("mi", "mi", "milla", "millas")
        add("ft", "ft", "pie", "pies")
        add("yd", "yd", "yarda", "yardas")
        add("kg", "kg", "kilo", "kilos", "kilogramo", "kilogramos")
        add("g", "g", "gramo", "gramos")
        add("lb", "lb", "libra", "libras")
        add("oz", "oz", "onza", "onzas")
        add("l", "l", "litro", "litros")
        add("gal", "gal", "galon", "galón", "galones")
        add("kmh", "kmh", "kilometros por hora", "kilómetros por hora")
        add("mph", "mph", "millas por hora")
        add("c", "c", "celsius", "centigrados", "centígrados", "grados celsius", "grados centigrados", "grados centígrados")
        add("f", "f", "fahrenheit", "grados fahrenheit")
    }

    private val UW = ALIAS.keys.sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    // "cuántas millas son 120 km"  |  "120 km a millas" / "pasa 3 galones a litros"
    private val TARGET_FIRST = spanishRegex(
        """cu[aá]nt[oa]s?\s+(?:grados\s+)?($UW)\s+(?:son|es|hay\s+en|equivalen?\s+a)\s+(.+?)\s+(?:grados\s+)?($UW)\b""",
    )
    private val VALUE_FIRST = spanishRegex(
        """(?:convierte\s+|pasa\s+|cu[aá]nt[oa]s?\s+(?:es|son)\s+)?(.+?)\s+(?:grados\s+)?($UW)\s+""" +
            """(?:a|en|son|equivalen?\s+a)\s+(?:grados\s+)?($UW)\b""",
    )

    fun convert(userInput: String): String? {
        val t = userInput.trim().trim('¿', '?', '¡', '!', '.', ' ').lowercase()

        TARGET_FIRST.find(t)?.let { m ->
            return run(m.groupValues[2], m.groupValues[3], m.groupValues[1])
        }
        VALUE_FIRST.find(t)?.let { m ->
            return run(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        return null
    }

    private fun run(rawValue: String, fromWord: String, toWord: String): String? {
        val value = SpanishNumbers.parse(rawValue) ?: return null
        val from = CANON[ALIAS[fromWord.trim()]] ?: return null
        val to = CANON[ALIAS[toWord.trim()]] ?: return null
        if (from.dim != to.dim) return null

        val result = if (from.dim == "temp") {
            when {
                ALIAS[fromWord.trim()] == "c" && ALIAS[toWord.trim()] == "f" -> value * 9 / 5 + 32
                ALIAS[fromWord.trim()] == "f" && ALIAS[toWord.trim()] == "c" -> (value - 32) * 5 / 9
                else -> value
            }
        } else {
            value * from.toBase / to.toBase
        }
        return "${SpanishNumbers.spoken(value)} ${from.spoken} son ${SpanishNumbers.spoken(round(result))} ${to.spoken}."
    }

    /** One decimal is plenty for a spoken conversion. */
    private fun round(n: Double): Double = kotlin.math.round(n * 10) / 10
}
