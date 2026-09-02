package dev.pgm.roadmate.domain.model

/**
 * A computed route, entirely from on-device data.
 *
 * @param points the line to draw, in order, as (lat, lon) pairs.
 * @param distanceMeters total driving distance.
 * @param durationSeconds estimated driving time.
 */
data class RouteResult(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Int,
    val durationSeconds: Int,
)
