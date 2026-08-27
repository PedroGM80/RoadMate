package dev.pgm.roadmate.domain.repository

interface MapSearchRepository {
    fun searchNearby(query: String, location: Pair<Double, Double>?)
}
