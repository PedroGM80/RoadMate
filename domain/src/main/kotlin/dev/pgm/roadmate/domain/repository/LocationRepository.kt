package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.UserLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for GPS access, owned by the domain layer per Dependency Inversion —
 * use cases depend on this abstraction, never on the data-layer implementation
 * or the Play Services APIs behind it.
 */
interface LocationRepository {

    /** Last known fix, updated on every successful [currentLocation] call. */
    val location: StateFlow<UserLocation?>

    /**
     * The current fix, or null when it can't be obtained (permission missing,
     * GPS disabled, no signal — tunnels, underground parking).
     */
    suspend fun currentLocation(forceRefresh: Boolean = false): UserLocation?
}
