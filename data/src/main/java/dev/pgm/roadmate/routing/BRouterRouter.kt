package dev.pgm.roadmate.routing

import android.content.Context
import btools.router.OsmPathElement
import btools.router.OsmTrack
import btools.router.ProfileCache
import btools.router.RoadMateVoiceHints
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.RouteManeuver
import dev.pgm.roadmate.domain.model.RouteResult
import dev.pgm.roadmate.domain.model.RouteStep
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.ml.DebugTrace
import dev.pgm.roadmate.utils.SegmentTiles
import kotlinx.coroutines.CancellationException
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
 * `RoutingEngine` on a background thread and returns the track geometry
 * together with its turn instructions.
 *
 * Returns null (no external fallback) when there's no data for the area or no
 * road connection is found.
 */
@Singleton
class BRouterRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataManager: RoutingDataManager,
) : RoutingRepository {

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
    }

    override val dataStatus: StateFlow<RoutingDataStatus> = dataManager.status

    private val profileDir: File by lazy {
        File(context.filesDir, "brouter/profile").apply { mkdirs() }
    }

    override suspend fun route(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): RouteResult? = withContext(Dispatchers.IO) {
        val tiles = SegmentTiles.namesFor(from, to)
        DebugTrace.log("route: $from -> $to, tiles=$tiles")
        if (!dataManager.ensureTiles(tiles)) {
            DebugTrace.log("route: ensureTiles false (no data / no network)")
            return@withContext null
        }

        val profile = runCatching { ensureProfile() }
            .onFailure { DebugTrace.log("route: profile unpack failed: ${it.message}") }
            .getOrNull() ?: return@withContext null

        runCatching {
            val rc = RoutingContext().apply {
                localFunction = profile.absolutePath
                // Without this BRouter computes geometry and nothing else — no
                // MessageData is kept on the path elements, so no instructions
                // can be derived afterwards at any price. Mode 2 (Locus) is
                // only about how *BRouter* would format them for export; all
                // that matters here is that it is non-zero, because the app
                // reads the structured hints and does its own wording.
                turnInstructionMode = TURN_INSTRUCTIONS_ON
                turnInstructionRoundabouts = true
            }
            // parseProfile's boolean is "reused from cache?", NOT success —
            // the first parse returns false. It throws on a real failure and
            // populates rc.expctxWay on success; check that instead.
            ProfileCache.parseProfile(rc)
            if (rc.expctxWay == null) {
                DebugTrace.log("route: profile not loaded (expctxWay null)")
                return@runCatching null
            }

            // BRouter waypoint strings are "lon,lat".
            val wpString = "%f,%f|%f,%f".format(
                Locale.US, from.second, from.first, to.second, to.first,
            )
            val waypoints = RoutingParamCollector().getWayPointList(wpString)

            val startedAt = System.currentTimeMillis()
            val engine = RoutingEngine(null, null, dataManager.segmentDir, waypoints, rc)
            engine.doRun(ROUTE_TIMEOUT_MS)
            val took = System.currentTimeMillis() - startedAt

            if (engine.errorMessage != null) {
                DebugTrace.log("route: no route (${took} ms): ${engine.errorMessage}")
                return@runCatching null
            }
            val track = engine.foundTrack ?: run {
                DebugTrace.log("route: foundTrack null (${took} ms)")
                return@runCatching null
            }
            val nodes: List<OsmPathElement> = track.nodes
            if (nodes.size < 2) {
                DebugTrace.log("route: only ${nodes.size} nodes (${took} ms)")
                return@runCatching null
            }

            val steps = stepsFrom(track, rc)
            DebugTrace.log(
                "route: OK ${took} ms, ${nodes.size} pts, ${track.distance} m, " +
                    "${track.totalSeconds} s, ${steps.size} steps"
            )
            RouteResult(
                points = nodes.map { it.iLat / 1e6 - 90.0 to it.iLon / 1e6 - 180.0 },
                distanceMeters = track.distance,
                durationSeconds = track.totalSeconds,
                steps = steps,
            )
        }.onFailure {
            if (it is CancellationException) throw it
            DebugTrace.log("route: threw ${it::class.simpleName}: ${it.message}")
        }.getOrNull()
    }

    /**
     * The route's instructions, in the app's own vocabulary.
     *
     * Instruction extraction is best-effort by design: a driver who gets a
     * drawn route and an ETA but no spoken turns is inconvenienced, while one
     * who gets an exception instead of a route is stranded. So everything from
     * here down swallows failure and returns an empty list.
     */
    private fun stepsFrom(track: OsmTrack, rc: RoutingContext): List<RouteStep> = runCatching {
        // Depending on the engine path taken, hints are either already
        // processed or still raw on the track. Asking twice is harmless.
        // VoiceHintList.list is package-private; RoadMateVoiceHints.from()
        // handles an empty/absent list itself, so just make sure hints were
        // processed at least once.
        if (track.voiceHints == null) {
            runCatching { track.processVoiceHints(rc) }
        }
        RoadMateVoiceHints.from(track).mapNotNull { hint ->
            val maneuver = maneuverOf(hint.command) ?: return@mapNotNull null
            RouteStep(
                maneuver = maneuver,
                latitude = hint.latitude,
                longitude = hint.longitude,
                distanceToNextMeters = hint.distanceToNextMeters.coerceAtLeast(0),
                roundaboutExit = hint.roundaboutExit.takeIf { it > 0 },
                roadName = hint.roadName,
                pointIndex = hint.pointIndex.coerceAtLeast(0),
            )
        }
    }.onFailure { DebugTrace.log("route: voice hints failed: ${it.message}") }
        .getOrDefault(emptyList())

    /**
     * BRouter command code to the app's maneuver vocabulary.
     *
     * The roundabout mapping is the one judgement call. BRouter emits RNDB for
     * right-hand traffic and RNLB for left-hand; driving on the right means
     * going *anticlockwise* around the island, which is what the car host
     * calls counter-clockwise. RoadMate is a Spanish app, so RNDB is the
     * common case and RNLB only shows up on a trip to Ireland or the UK.
     */
    private fun maneuverOf(command: Int): RouteManeuver? = when (command) {
        CMD_CONTINUE, CMD_BEELINE -> RouteManeuver.STRAIGHT
        CMD_TURN_LEFT -> RouteManeuver.TURN_LEFT
        CMD_TURN_SLIGHT_LEFT -> RouteManeuver.TURN_SLIGHT_LEFT
        CMD_TURN_SHARP_LEFT -> RouteManeuver.TURN_SHARP_LEFT
        CMD_TURN_RIGHT -> RouteManeuver.TURN_RIGHT
        CMD_TURN_SLIGHT_RIGHT -> RouteManeuver.TURN_SLIGHT_RIGHT
        CMD_TURN_SHARP_RIGHT -> RouteManeuver.TURN_SHARP_RIGHT
        CMD_KEEP_LEFT, CMD_EXIT_LEFT -> RouteManeuver.KEEP_LEFT
        CMD_KEEP_RIGHT, CMD_EXIT_RIGHT -> RouteManeuver.KEEP_RIGHT
        CMD_U_TURN_LEFT, CMD_U_TURN -> RouteManeuver.U_TURN_LEFT
        CMD_U_TURN_RIGHT -> RouteManeuver.U_TURN_RIGHT
        CMD_ROUNDABOUT_RIGHT_HAND -> RouteManeuver.ROUNDABOUT_CCW
        CMD_ROUNDABOUT_LEFT_HAND -> RouteManeuver.ROUNDABOUT_CW
        CMD_OFF_ROUTE -> RouteManeuver.OFF_ROUTE
        CMD_END -> RouteManeuver.DESTINATION
        else -> null
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
        const val ROUTE_TIMEOUT_MS = 25_000L

        /** Any non-zero value makes BRouter keep the data instructions need. */
        const val TURN_INSTRUCTIONS_ON = 2

        // btools.router.VoiceHint command codes. They are package-private
        // constants upstream, so they are restated — and only ever compared
        // against, never passed back in.
        const val CMD_CONTINUE = 1
        const val CMD_TURN_LEFT = 2
        const val CMD_TURN_SLIGHT_LEFT = 3
        const val CMD_TURN_SHARP_LEFT = 4
        const val CMD_TURN_RIGHT = 5
        const val CMD_TURN_SLIGHT_RIGHT = 6
        const val CMD_TURN_SHARP_RIGHT = 7
        const val CMD_KEEP_LEFT = 8
        const val CMD_KEEP_RIGHT = 9
        const val CMD_U_TURN_LEFT = 10
        const val CMD_U_TURN_RIGHT = 11
        const val CMD_OFF_ROUTE = 12
        const val CMD_ROUNDABOUT_RIGHT_HAND = 13
        const val CMD_ROUNDABOUT_LEFT_HAND = 14
        const val CMD_U_TURN = 15
        const val CMD_BEELINE = 16
        const val CMD_EXIT_LEFT = 17
        const val CMD_EXIT_RIGHT = 18
        const val CMD_END = 100
    }
}
