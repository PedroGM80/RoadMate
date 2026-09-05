package dev.pgm.roadmate.domain.model

import java.util.Date

/**
 * Snapshot of the trip state used to build a Gemini Nano prompt.
 */
data class TravelContext(
    val currentLocation: UserLocation?,
    val destination: String? = null,
    val hour: Int,
    val date: Date,
    val userInput: String,
    val weatherDescription: String? = null,
    val minute: Int = 0,
    /** "calle · localidad" resolved from the offline map, when the Mapa tab has been open. */
    val placeLabel: String? = null,
)
