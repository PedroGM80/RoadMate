package dev.pgm.roadmate.domain.model

/**
 * A computed route, entirely from on-device data.
 *
 * @param points the line to draw, in order, as (lat, lon) pairs.
 * @param distanceMeters total driving distance.
 * @param durationSeconds estimated driving time.
 * @param steps the turn-by-turn instructions along [points], in order. Empty
 *   when the router produced geometry but no usable instructions — the route
 *   still draws and still has an ETA, it just can't be announced.
 */
data class RouteResult(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<RouteStep> = emptyList(),
)
