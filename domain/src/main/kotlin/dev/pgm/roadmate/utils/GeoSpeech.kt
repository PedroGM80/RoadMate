package dev.pgm.roadmate.utils

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SPANISH: Locale = Locale.forLanguageTag("es-ES")

private val COMPASS = listOf(
    "norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste",
)

/** Great-circle distance in metres between two `(lat, lon)` points. */
fun haversineMetres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.first - a.first)
    val dLon = Math.toRadians(b.second - a.second)
    val la1 = Math.toRadians(a.first)
    val la2 = Math.toRadians(b.first)
    val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(h)))
}

/** The compass word for the bearing from [a] to [b]: "norte", "noreste", … */
fun compassWord(a: Pair<Double, Double>, b: Pair<Double, Double>): String {
    val dLon = Math.toRadians(b.second - a.second)
    val la1 = Math.toRadians(a.first)
    val la2 = Math.toRadians(b.first)
    val y = sin(dLon) * cos(la2)
    val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
    val deg = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    return COMPASS[(((deg + 22.5) / 45.0).toInt()) % 8]
}

/** Distance from [a] to [b] spoken in Spanish: "120 metros" / "3,4 km". */
fun distanceInWords(a: Pair<Double, Double>, b: Pair<Double, Double>): String {
    val metres = haversineMetres(a, b)
    return if (metres < 950) {
        "${((metres / 10).roundToInt() * 10).coerceAtLeast(10)} metros"
    } else {
        "%.1f km".format(SPANISH, metres / 1000.0)
    }
}

/** A `"lat,lon"` string → coordinate pair, or null when it doesn't parse. */
fun parseCoords(value: String): Pair<Double, Double>? {
    val parts = value.split(",").map { it.trim().toDoubleOrNull() }
    return if (parts.size == 2 && parts[0] != null && parts[1] != null) {
        parts[0]!! to parts[1]!!
    } else {
        null
    }
}
