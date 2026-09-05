package dev.pgm.roadmate.car

import dev.pgm.roadmate.presentation.map.PoiKind

/** One pinned place on the car map: what to call it and where it is. */
data class CarPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: PoiKind? = null,
)
