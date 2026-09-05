package dev.pgm.roadmate.car

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.Step
import dev.pgm.roadmate.domain.model.RouteManeuver
import dev.pgm.roadmate.domain.model.RouteStep
import dev.pgm.roadmate.domain.model.UserLocation
import kotlin.math.roundToInt

/**
 * Translation between RoadMate's [RouteStep] / [RouteManeuver] and the Car App
 * Library's navigation types, plus the Spanish wording and distance formatting
 * for spoken and on-card instructions. Pure — no state.
 */

internal fun stepFor(step: RouteStep): Step = Step.Builder()
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
internal fun maneuverFor(step: RouteStep): Maneuver {
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
internal fun phrase(step: RouteStep): String = when (step.maneuver) {
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
internal fun spokenDistance(metres: Double): String {
    if (metres >= 1000) {
        val km = metres / 1000.0
        return if (km >= 10) "${km.roundToInt()} kilómetros" else "%,.1f kilómetros".format(km)
    }
    val rounded = (metres / 50).roundToInt() * 50
    return "${rounded.coerceAtLeast(50)} metros"
}

/** Metres under a kilometre, kilometres above — the unit a driver expects. */
internal fun distanceOf(metres: Double): Distance =
    if (metres >= 1000) {
        Distance.create(metres / 1000.0, Distance.UNIT_KILOMETERS)
    } else {
        Distance.create(metres, Distance.UNIT_METERS)
    }

internal fun metresBetween(from: UserLocation, to: RouteStep): Double =
    haversineMetres(from.latitude, from.longitude, to.latitude, to.longitude)
