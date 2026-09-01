package dev.pgm.roadmate.domain.model

/** One question the driver asked and the answer RoadMate gave. */
data class Exchange(
    val question: String,
    val answer: String,
)
