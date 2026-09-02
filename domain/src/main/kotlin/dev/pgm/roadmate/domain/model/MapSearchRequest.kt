package dev.pgm.roadmate.domain.model

/**
 * A place the driver asked for out loud, to be shown on the in-app offline
 * map. [category] is set when the query was a kind of place ("gasolineras");
 * null means [rawQuery] names a specific place to match by name against the
 * downloaded tiles. [origin] is the current location, for centring and (later)
 * routing.
 */
data class MapSearchRequest(
    val rawQuery: String,
    val category: PlaceCategory?,
    val origin: Pair<Double, Double>?,
    /** true → the driver asked to be taken there; draw a route, not just a pin. */
    val navigate: Boolean = false,
    /**
     * A coordinate already resolved from memory (home / work) to route
     * straight to — when set, the map skips POI search and just routes
     * [origin] → [destination].
     */
    val destination: Pair<Double, Double>? = null,
)
