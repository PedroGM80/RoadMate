package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for GPS access, owned by the domain layer per Dependency Inversion —
 * use cases depend on this abstraction, never on the data-layer implementation
 * or the Play Services APIs behind it.
 */
interface LocationRepository {

    /** Last known coordinates, updated on every successful fetch. */
    val location: StateFlow<Pair<Double, Double>?>

    /**
     * Returns the current (latitude, longitude), or null if unavailable
     * (permission missing, GPS disabled, no fix — e.g. tunnels, parking garages).
     */
    suspend fun getCurrentCoordinates(forceRefresh: Boolean = false): Pair<Double, Double>?
}
