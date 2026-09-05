package dev.pgm.roadmate.car

/** One pinned place on the car map: what to call it and where it is. */
data class CarPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
