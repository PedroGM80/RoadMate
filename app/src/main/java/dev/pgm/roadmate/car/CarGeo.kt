package dev.pgm.roadmate.car

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance in metres between two `(lat, lon)` points. */
internal fun haversineMetres(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double,
): Double {
    val dLat = Math.toRadians(toLat - fromLat)
    val dLon = Math.toRadians(toLon - fromLon)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
        sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}

/** Route-summary distance: "820 m" under a kilometre, "3,4 km" above. */
internal fun formatDistance(metres: Int): String =
    if (metres < 1000) "$metres m" else "%,.1f km".format(metres / 1000.0)

/** Route-summary duration: "45 min" / "1 h 20 min". */
internal fun formatDuration(seconds: Int): String {
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    if (minutes < 60) return "$minutes min"
    return "${minutes / 60} h ${minutes % 60} min"
}
