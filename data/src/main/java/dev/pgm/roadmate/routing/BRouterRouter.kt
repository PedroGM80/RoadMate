package dev.pgm.roadmate.routing

import android.content.Context
import android.util.Log
import btools.router.OsmPathElement
import btools.router.ProfileCache
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.RouteResult
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.utils.SegmentTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline routing via the BRouter engine (pure Java). Unpacks the bundled
 * `car` profile + `lookups.dat` on first use, makes sure the `.rd5` segment
 * tiles for the route's area are on disk ([RoutingDataManager]), then runs
 * `RoutingEngine` on a background thread and returns the track geometry.
 *
 * Returns null (no external fallback) when there's no data for the area or no
 * road connection is found.
 */
@Singleton
class BRouterRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataManager: RoutingDataManager,
) : RoutingRepository {

    override val dataStatus: StateFlow<RoutingDataStatus> = dataManager.status

    private val profileDir: File by lazy {
        File(context.filesDir, "brouter/profile").apply { mkdirs() }
    }

    override suspend fun route(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): RouteResult? = withContext(Dispatchers.IO) {
        val tiles = SegmentTiles.namesFor(from, to)
        if (!dataManager.ensureTiles(tiles)) return@withContext null

        val profile = runCatching { ensureProfile() }.getOrNull() ?: return@withContext null

        runCatching {
            val rc = RoutingContext().apply { localFunction = profile.absolutePath }
            if (!ProfileCache.parseProfile(rc)) {
                Log.w(TAG, "profile parse failed")
                return@runCatching null
            }

            // BRouter waypoint strings are "lon,lat".
            val wpString = "%f,%f|%f,%f".format(
                Locale.US, from.second, from.first, to.second, to.first,
            )
            val waypoints = RoutingParamCollector().getWayPointList(wpString)

            val engine = RoutingEngine(null, null, dataManager.segmentDir, waypoints, rc)
            engine.doRun(ROUTE_TIMEOUT_MS)

            if (engine.errorMessage != null) {
                Log.w(TAG, "no route: ${engine.errorMessage}")
                return@runCatching null
            }
            val track = engine.foundTrack ?: return@runCatching null
            val nodes: List<OsmPathElement> = track.nodes
            if (nodes.size < 2) return@runCatching null

            RouteResult(
                points = nodes.map { it.iLat / 1e6 - 90.0 to it.iLon / 1e6 - 180.0 },
                distanceMeters = track.distance,
                durationSeconds = track.totalSeconds,
            )
        }.onFailure { Log.w(TAG, "routing failed", it) }.getOrNull()
    }

    /** Copies `car.brf` + `lookups.dat` out of assets once; returns the profile file. */
    private fun ensureProfile(): File {
        val profile = File(profileDir, "car.brf")
        copyAssetIfMissing("brouter/car.brf", profile)
        copyAssetIfMissing("brouter/lookups.dat", File(profileDir, "lookups.dat"))
        return profile
    }

    private fun copyAssetIfMissing(assetPath: String, target: File) {
        if (target.isFile && target.length() > 0L) return
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    private companion object {
        const val TAG = "BRouterRouter"
        const val ROUTE_TIMEOUT_MS = 25_000L
    }
}
