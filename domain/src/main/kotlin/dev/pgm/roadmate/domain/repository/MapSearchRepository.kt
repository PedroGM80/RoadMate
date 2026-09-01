package dev.pgm.roadmate.domain.repository

interface MapSearchRepository {
    /**
     * Fires a `geo:` search at whatever Maps app is installed. Returns false
     * if the device has no app that handles `geo:` intents, so the caller can
     * say so instead of pretending the search happened.
     */
    fun searchNearby(query: String, location: Pair<Double, Double>?): Boolean
}
