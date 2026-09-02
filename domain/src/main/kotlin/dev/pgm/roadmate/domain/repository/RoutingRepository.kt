package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.RouteResult
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device turn-by-turn routing (BRouter). No network at route time beyond
 * a one-off, Wi-Fi-gated download of the segment tile(s) for the area — no
 * account, no external app.
 */
interface RoutingRepository {

    /** Progress of any segment-tile download the last [route] call needed. */
    val dataStatus: StateFlow<RoutingDataStatus>

    /**
     * Routes from [from] to [to] (both lat, lon). Returns null when the route
     * can't be built — no data for the area (and the download couldn't run),
     * or no road connection found. Downloads the needed `.rd5` tile first if
     * it's missing and Wi-Fi is available.
     */
    suspend fun route(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): RouteResult?
}
