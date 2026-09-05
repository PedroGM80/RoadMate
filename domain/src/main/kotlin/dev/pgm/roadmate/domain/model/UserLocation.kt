package dev.pgm.roadmate.domain.model

/** The user's position and speed, free of any Android location type. */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Int? = null,
)

/** As a `(lat, lon)` pair, for the routing and geometry code that works in plain points. */
fun UserLocation.latLon(): Pair<Double, Double> = latitude to longitude
