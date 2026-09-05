package dev.pgm.roadmate.domain.model

/**
 * One instruction along a route: what to do, where, and how far the driver
 * travels after it before the next one.
 *
 * This is the domain's own vocabulary, deliberately not BRouter's and not the
 * Car App Library's. The router speaks in integer command codes and the car
 * host in `Maneuver.TYPE_*` constants; keeping a plain enum between them means
 * the phone UI, the car screen and the spoken announcements all read from one
 * model, and swapping either end doesn't ripple.
 *
 * @param maneuver what the driver does at this point.
 * @param latitude where the maneuver happens.
 * @param longitude where the maneuver happens.
 * @param distanceToNextMeters distance from here to the next instruction.
 * @param roundaboutExit which exit to take, 1-based, or null when [maneuver]
 *   is not a roundabout.
 * @param roadName the road being joined, when the router knows it.
 * @param pointIndex index into [RouteResult.points] of the matching geometry
 *   point, so progress along the line can be tracked without re-matching.
 */
data class RouteStep(
    val maneuver: RouteManeuver,
    val latitude: Double,
    val longitude: Double,
    val distanceToNextMeters: Int,
    val roundaboutExit: Int? = null,
    val roadName: String? = null,
    val pointIndex: Int = 0,
)

/**
 * The set of maneuvers RoadMate distinguishes. Deliberately small: a driver
 * glancing at a screen needs "left", "sharp left", "keep left" to look
 * different and nothing finer than that.
 */
enum class RouteManeuver {
    STRAIGHT,
    TURN_LEFT,
    TURN_SLIGHT_LEFT,
    TURN_SHARP_LEFT,
    TURN_RIGHT,
    TURN_SLIGHT_RIGHT,
    TURN_SHARP_RIGHT,
    KEEP_LEFT,
    KEEP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    ROUNDABOUT_CW,
    ROUNDABOUT_CCW,
    OFF_ROUTE,
    DESTINATION,
}
