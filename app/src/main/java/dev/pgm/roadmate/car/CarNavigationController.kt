package dev.pgm.roadmate.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import dev.pgm.roadmate.domain.model.RouteManeuver
import dev.pgm.roadmate.domain.model.RouteResult
import dev.pgm.roadmate.domain.model.RouteStep
import dev.pgm.roadmate.domain.model.UserLocation
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a computed route into actual guidance: the arrow and the "en 300
 * metros, gire a la derecha" the driver sees and hears.
 *
 * Drawing a line on a map is not navigating, and the car host draws that
 * distinction sharply. Until an app calls [NavigationManager.navigationStarted]
 * it is a map viewer: the host gives it no guidance surface, puts nothing in
 * the instrument cluster, and — the part that surprises people — does not treat
 * its spoken output as turn instructions. RoadMate computed perfectly good
 * routes and never made that call, so the car drew a line, said nothing, and
 * told the driver nothing.
 *
 * Three things have to stay in step for guidance to work, and they are all
 * here:
 *
 * - the **host**, told navigation started and fed a [Trip] as the car moves;
 * - the **screen**, which reads [routingInfo] and [travelEstimate] whenever it
 *   is invalidated;
 * - the **driver**, told about a turn twice — once with time to change lane,
 *   once at the turn itself.
 *
 * Position tracking is deliberately simple: straight-line distance to the next
 * maneuver point, advancing once the car is within [ARRIVED_METERS] of it. With
 * a maneuver every few hundred metres that lands within a couple of seconds of
 * the real thing, and it cannot wedge itself the way matching onto a polyline
 * can when the fix jumps. What it does not do is notice going off-route — see
 * [OFF_ROUTE_METERS].
 */
class CarNavigationController(
    carContext: CarContext,
    private val locationRepository: LocationRepository,
    private val speech: SpeechSynthesisRepository,
    private val scope: CoroutineScope,
    /** Invoked whenever something the screen renders has changed. */
    private val onChanged: () -> Unit,
) {

    /** The instruction card, or null when nothing is being navigated. */
    var routingInfo: RoutingInfo? = null
        private set

    /** Remaining distance and arrival time to the destination. */
    var travelEstimate: TravelEstimate? = null
        private set

    val isNavigating: Boolean get() = trackingJob != null

    private var trackingJob: Job? = null
    private var route: RouteResult? = null
    private var destinationLatitude = 0.0
    private var destinationLongitude = 0.0
    private var destinationName = ""

    /** Index into [RouteResult.steps] of the maneuver being driven towards. */
    private var stepIndex = 0

    /** Which announcements the driver has already heard for [stepIndex]. */
    private var announcedFar = false
    private var announcedNear = false

    private val navigationManager: NavigationManager? =
        runCatching { carContext.getCarService(NavigationManager::class.java) }
            .onFailure { Log.w(TAG, "no NavigationManager: ${it.message}") }
            .getOrNull()

    /**
     * The host can end navigation on its own — the driver started another
     * navigation app, or pressed stop in the car's own UI. Ignoring that leaves
     * two apps both convinced they own the guidance surface.
     */
    private val callback = object : NavigationManagerCallback {
        override fun onStopNavigation() {
            stop(notifyHost = false)
        }

        override fun onAutoDriveEnabled() = Unit
    }

    /**
     * Begins guidance to [name] at ([latitude], [longitude]) along [result].
     *
     * Returns false when the host refused to hand navigation over — another app
     * holds it, or this build isn't recognised as a navigation app. The route
     * still draws in that case; only the guidance is missing, and the caller
     * should say so rather than pretend otherwise.
     */
    fun start(name: String, latitude: Double, longitude: Double, result: RouteResult): Boolean {
        stop()
        val manager = navigationManager ?: return false
        val started = runCatching {
            manager.setNavigationManagerCallback(callback)
            manager.navigationStarted()
        }.onFailure { Log.w(TAG, "navigationStarted failed: ${it.message}") }.isSuccess
        if (!started) return false

        route = result
        destinationLatitude = latitude
        destinationLongitude = longitude
        destinationName = name
        stepIndex = 0
        announcedFar = false
        announcedNear = false

        // Publish once immediately so the first instruction is on screen before
        // the car has moved a metre.
        refresh(locationRepository.location.value)
        trackingJob = scope.launch { track() }
        return true
    }

    /**
     * Ends guidance. [notifyHost] is false only when the host is the one that
     * ended it, since telling it about its own decision throws.
     */
    fun stop(notifyHost: Boolean = true) {
        val wasNavigating = trackingJob != null
        trackingJob?.cancel()
        trackingJob = null
        route = null
        routingInfo = null
        travelEstimate = null
        stepIndex = 0
        if (notifyHost && wasNavigating) {
            navigationManager?.let { runCatching { it.navigationEnded() } }
        }
        if (wasNavigating) onChanged()
    }

    /**
     * Releases the host callback. Must run before the session goes away — a
     * callback left registered on a dead session is what has the host report
     * the app as unresponsive on the next connection.
     */
    fun release() {
        stop()
        navigationManager?.let { runCatching { it.clearNavigationManagerCallback() } }
    }

    private suspend fun track() {
        while (scope.isActive && route != null) {
            delay(TICK_MS)
            val fix = try {
                locationRepository.currentLocation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "location tick failed: ${error.message}")
                null
            } ?: continue
            refresh(fix)
        }
    }

    /**
     * Recomputes where the car is along the route and publishes it — to the
     * host as a [Trip], to the screen as [routingInfo], and to the driver as
     * speech when a turn comes into range.
     */
    private fun refresh(fix: UserLocation?) {
        val result = route ?: return
        val steps = result.steps
        if (fix == null || steps.isEmpty()) {
            // No instructions from the router (or no fix yet): the trip still
            // gets an estimate so the host has something to show, but there is
            // nothing to announce and no arrow to draw.
            travelEstimate = estimate(result.distanceMeters, result.durationSeconds)
            publishTrip(null, null)
            onChanged()
            return
        }

        var target = steps.getOrNull(stepIndex) ?: run { arrive(); return }
        var distanceToStep = metresBetween(fix, target)

        // Advance past every maneuver already driven through. One tick can
        // cover more than one when they are metres apart — a slip road onto a
        // roundabout — so this is a loop, not an if.
        while (distanceToStep < ARRIVED_METERS && stepIndex < steps.lastIndex) {
            stepIndex++
            announcedFar = false
            announcedNear = false
            target = steps[stepIndex]
            distanceToStep = metresBetween(fix, target)
        }
        if (stepIndex >= steps.lastIndex && distanceToStep < ARRIVED_METERS) {
            arrive()
            return
        }

        val remainingMeters = remainingMeters(distanceToStep, steps)
        travelEstimate = estimate(remainingMeters, remainingSeconds(result, remainingMeters))

        val current = stepFor(target)
        val next = steps.getOrNull(stepIndex + 1)?.let { stepFor(it) }
        routingInfo = runCatching {
            RoutingInfo.Builder()
                .setCurrentStep(current, distanceOf(distanceToStep))
                .apply { next?.let { setNextStep(it) } }
                .build()
        }.onFailure { Log.w(TAG, "RoutingInfo build failed: ${it.message}") }.getOrNull()

        publishTrip(current, target)
        announce(target, distanceToStep)
        onChanged()
    }

    /** Speaks a maneuver twice: once with room to react, once at the turn. */
    private fun announce(step: RouteStep, distanceToStep: Double) {
        if (!announcedFar && distanceToStep in NEAR_ANNOUNCE_METERS..FAR_ANNOUNCE_METERS) {
            announcedFar = true
            speech.speak("En ${spokenDistance(distanceToStep)}, ${phrase(step).replaceFirstChar { it.lowercase() }}")
            return
        }
        if (!announcedNear && distanceToStep < NEAR_ANNOUNCE_METERS) {
            announcedNear = true
            // Suppress the far announcement too: if the car was already close
            // when this step became current, saying both back to back is noise.
            announcedFar = true
            speech.speak(phrase(step))
        }
    }

    private fun arrive() {
        speech.speak(ARRIVED)
        stop()
    }

    /**
     * Hands the host the trip. This is what puts the instruction in the
     * instrument cluster and the head-up display, where the driver's eyes
     * already are — the template only covers the centre screen.
     */
    private fun publishTrip(current: Step?, target: RouteStep?) {
        val manager = navigationManager ?: return
        val estimate = travelEstimate ?: return
        runCatching {
            val trip = Trip.Builder()
                .addDestination(
                    Destination.Builder()
                        .setName(destinationName.ifBlank { DESTINATION_FALLBACK })
                        .build(),
                    estimate,
                )
                .apply {
                    if (current != null) addStep(current, estimate)
                    target?.roadName?.takeIf { it.isNotBlank() }?.let { setCurrentRoad(it) }
                }
                .build()
            manager.updateTrip(trip)
        }.onFailure { Log.w(TAG, "updateTrip failed: ${it.message}") }
    }

    /** Distance from the car to the destination, following the remaining steps. */
    private fun remainingMeters(distanceToStep: Double, steps: List<RouteStep>): Int {
        val afterCurrent = steps.drop(stepIndex).sumOf { it.distanceToNextMeters.toLong() }
        return (distanceToStep.roundToInt().toLong() + afterCurrent)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    /**
     * Remaining time, scaled from the route's own total rather than recomputed:
     * BRouter's estimate already accounts for road types and speed limits, and
     * a share of it beats anything a constant average speed gives.
     */
    private fun remainingSeconds(result: RouteResult, remainingMeters: Int): Int {
        if (result.distanceMeters <= 0) return result.durationSeconds
        val share = remainingMeters.toDouble() / result.distanceMeters
        return (result.durationSeconds * share).roundToInt().coerceAtLeast(0)
    }

    private fun estimate(metres: Int, seconds: Int): TravelEstimate? = runCatching {
        val arrival = System.currentTimeMillis() + seconds * 1_000L
        TravelEstimate.Builder(
            Distance.create(metres / 1000.0, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(arrival, TimeZone.getDefault()),
        )
            .setRemainingTimeSeconds(seconds.toLong())
            .build()
    }.onFailure { Log.w(TAG, "TravelEstimate build failed: ${it.message}") }.getOrNull()

    private fun stepFor(step: RouteStep): Step = Step.Builder()
        .setCue(phrase(step))
        .setManeuver(maneuverFor(step))
        .apply { step.roadName?.takeIf { it.isNotBlank() }?.let { setRoad(it) } }
        .build()

    /**
     * The host's own maneuver type, so it draws its own arrow — one that
     * matches the rest of the car's UI and is legible at a glance in a way an
     * app-supplied icon rarely is.
     *
     * A roundabout without an exit number is not a valid enter-and-exit
     * maneuver and `build()` throws, so those fall back to the plain enter type.
     */
    private fun maneuverFor(step: RouteStep): Maneuver {
        val roundabout = step.maneuver == RouteManeuver.ROUNDABOUT_CW ||
            step.maneuver == RouteManeuver.ROUNDABOUT_CCW
        if (roundabout) {
            val clockwise = step.maneuver == RouteManeuver.ROUNDABOUT_CW
            val exit = step.roundaboutExit
            if (exit != null && exit > 0) {
                val type = if (clockwise) {
                    Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
                } else {
                    Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
                }
                return Maneuver.Builder(type).setRoundaboutExitNumber(exit).build()
            }
            val type =
                if (clockwise) Maneuver.TYPE_ROUNDABOUT_ENTER_CW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
            return Maneuver.Builder(type).build()
        }
        return Maneuver.Builder(
            when (step.maneuver) {
                RouteManeuver.STRAIGHT -> Maneuver.TYPE_STRAIGHT
                RouteManeuver.TURN_LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
                RouteManeuver.TURN_SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                RouteManeuver.TURN_SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
                RouteManeuver.TURN_RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                RouteManeuver.TURN_SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                RouteManeuver.TURN_SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
                RouteManeuver.KEEP_LEFT -> Maneuver.TYPE_KEEP_LEFT
                RouteManeuver.KEEP_RIGHT -> Maneuver.TYPE_KEEP_RIGHT
                RouteManeuver.U_TURN_LEFT -> Maneuver.TYPE_U_TURN_LEFT
                RouteManeuver.U_TURN_RIGHT -> Maneuver.TYPE_U_TURN_RIGHT
                RouteManeuver.OFF_ROUTE -> Maneuver.TYPE_UNKNOWN
                RouteManeuver.DESTINATION -> Maneuver.TYPE_DESTINATION
                // Handled above; here only so the when stays exhaustive.
                RouteManeuver.ROUNDABOUT_CW -> Maneuver.TYPE_ROUNDABOUT_ENTER_CW
                RouteManeuver.ROUNDABOUT_CCW -> Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
            }
        ).build()
    }

    /** What the driver reads on the card and hears out loud. */
    private fun phrase(step: RouteStep): String = when (step.maneuver) {
        RouteManeuver.STRAIGHT -> "Siga recto"
        RouteManeuver.TURN_LEFT -> "Gire a la izquierda"
        RouteManeuver.TURN_SLIGHT_LEFT -> "Gire ligeramente a la izquierda"
        RouteManeuver.TURN_SHARP_LEFT -> "Gire bruscamente a la izquierda"
        RouteManeuver.TURN_RIGHT -> "Gire a la derecha"
        RouteManeuver.TURN_SLIGHT_RIGHT -> "Gire ligeramente a la derecha"
        RouteManeuver.TURN_SHARP_RIGHT -> "Gire bruscamente a la derecha"
        RouteManeuver.KEEP_LEFT -> "Manténgase a la izquierda"
        RouteManeuver.KEEP_RIGHT -> "Manténgase a la derecha"
        RouteManeuver.U_TURN_LEFT, RouteManeuver.U_TURN_RIGHT -> "Cambie de sentido"
        RouteManeuver.ROUNDABOUT_CW, RouteManeuver.ROUNDABOUT_CCW ->
            step.roundaboutExit?.let { "En la rotonda, tome la salida $it" } ?: "Entre en la rotonda"
        RouteManeuver.OFF_ROUTE -> "Vuelva a la ruta"
        RouteManeuver.DESTINATION -> "Ha llegado a su destino"
    }

    /** Rounded the way a person says it: "300 metros", not "287 metros". */
    private fun spokenDistance(metres: Double): String {
        if (metres >= 1000) {
            val km = metres / 1000.0
            return if (km >= 10) "${km.roundToInt()} kilómetros" else "%,.1f kilómetros".format(km)
        }
        val rounded = (metres / 50).roundToInt() * 50
        return "${rounded.coerceAtLeast(50)} metros"
    }

    /** Metres under a kilometre, kilometres above — the unit a driver expects. */
    private fun distanceOf(metres: Double): Distance =
        if (metres >= 1000) {
            Distance.create(metres / 1000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(metres, Distance.UNIT_METERS)
        }

    private fun metresBetween(from: UserLocation, to: RouteStep): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
            sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    private companion object {
        const val TAG = "CarNavigation"

        /** How often the car's position is re-read while navigating. */
        const val TICK_MS = 2_500L

        /** Within this of a maneuver point, the driver is considered past it. */
        const val ARRIVED_METERS = 30.0

        /** First announcement: far enough out to change lane. */
        const val FAR_ANNOUNCE_METERS = 400.0

        /** Second announcement: at the turn itself. */
        const val NEAR_ANNOUNCE_METERS = 90.0

        /**
         * Not used yet, and named so the gap is visible: RoadMate does not
         * re-route when the driver leaves the line. Detecting that needs
         * distance to the *polyline*, not to the next maneuver, plus a fresh
         * BRouter run — deliberately out of scope here rather than half-done.
         */
        const val OFF_ROUTE_METERS = 120.0

        const val EARTH_RADIUS_M = 6_371_000.0

        const val ARRIVED = "Ha llegado a su destino."
        const val DESTINATION_FALLBACK = "Destino"
    }
}
