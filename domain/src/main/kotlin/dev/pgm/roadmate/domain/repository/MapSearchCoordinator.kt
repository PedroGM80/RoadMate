package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.MapSearchRequest
import kotlinx.coroutines.flow.Flow

/**
 * The bridge between "the driver asked for a place out loud" and "show it on
 * the in-app offline map". The voice pipeline [submit]s a request; the map
 * layer collects [requests], switches to the map, and pins the result. There
 * is no external-Maps handoff — everything stays on the downloaded tiles.
 */
interface MapSearchCoordinator {

    /** Voice searches to display on the map, in order. */
    val requests: Flow<MapSearchRequest>

    /** True when at least one map region is downloaded and usable offline. */
    fun hasOfflineMap(): Boolean

    suspend fun submit(request: MapSearchRequest)
}
