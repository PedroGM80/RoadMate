package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.model.DateTimeWithZone
import java.util.TimeZone
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.PlaceCategory
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.presentation.map.OfflineMapController
import dev.pgm.roadmate.presentation.map.PoiKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RoadMate's own map on the car screen, with its own controls.
 *
 * [MapWithContentTemplate] is the arrangement the phone already has: the map
 * fills the pane, a list sits over it, and the host draws a strip of map
 * controls down the side. The map itself is drawn by [CarMapRenderer] onto the
 * surface the host hands out — which is why this only exists in the navigation
 * category, the only one granted `androidx.car.app.ACCESS_SURFACE`.
 *
 * The list is one level, not two. A category expands in place with the places
 * it found underneath it, and tapping the category again collapses them:
 * pushing a second screen for the results would move this one to STOPPED, and
 * with it the map.
 *
 * Controls are icons without labels. A driver gets a glance, and the host
 * accepts title-less actions precisely because a known glyph reads faster
 * than a word.
 */
class NavigationCarScreen(
    carContext: CarContext,
    private val renderer: CarMapRenderer,
    private val locationRepository: LocationRepository,
    private val currentPlaceRepository: CurrentPlaceRepository,
    private val mapSearchCoordinator: MapSearchCoordinator,
    private val routingRepository: RoutingRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val offlineMap: OfflineMapController,
) : Screen(carContext) {

    /** Which category is pinned on the map; null means none is expanded. */
    private var poiKind: PoiKind? = null

    /**
     * The active route's estimate, shown by the host in its own banner rather
     * than as a row: with the list hidden there is no list to put it in, and
     * a TravelEstimate is what a navigation host is built to display.
     */
    private var travelEstimate: TravelEstimate? = null

    /** Set while the route is being computed, so the driver sees something. */
    private var routing = false
    private var routeJob: Job? = null

    init {
        lifecycleScope.launch {
            locationRepository.getCurrentCoordinates()
            renderer.centreOnDriver(animate = false)
            invalidate()
        }
        lifecycleScope.launch {
            currentPlaceRepository.label.collect { invalidate() }
        }
        // "busca gasolineras" said out loud lands on the same pins as tapping
        // the row. The coordinator is the one channel the voice pipeline
        // publishes searches on, and the phone's map listens to it too.
        lifecycleScope.launch {
            mapSearchCoordinator.requests.collect { request ->
                val kind = request.category?.let(PoiKind::from) ?: return@collect
                select(kind)
            }
        }
        // The offline status starts as Unknown until something asks; the
        // settings screen would otherwise be the only place that ever does.
        offlineMap.refresh()
    }

    /**
     * Map only until there is something to say.
     *
     * With no category chosen the screen is a [NavigationTemplate]: nothing
     * but the map and two strips of icons, which is the whole point of drawing
     * our own map in the car. Choosing a category swaps in
     * [MapWithContentTemplate] so the results have somewhere to live, and
     * tapping the same category again collapses the panel back off the map.
     *
     * Everything in the strips is an icon with no label. A driver gets a
     * glance, and the host accepts title-less actions precisely because a
     * known glyph reads faster than a word — it also keeps the strip narrow.
     */
    override fun onGetTemplate(): Template {
        val mapStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(mapAction(R.drawable.lucide_ic_locate_fixed) { recentre() })
            .addAction(mapAction(R.drawable.lucide_ic_zoom_in) { renderer.zoomIn() })
            .addAction(mapAction(R.drawable.lucide_ic_zoom_out) { renderer.zoomOut() })
            .build()

        if (poiKind == null) {
            return NavigationTemplate.Builder()
                .setMapActionStrip(mapStrip)
                .setActionStrip(categoryStrip())
                .apply { travelEstimate?.let { setDestinationTravelEstimate(it) } }
                .build()
        }

        return MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate())
            .setMapController(MapController.Builder().setMapActionStrip(mapStrip).build())
            .setActionStrip(categoryStrip())
            .build()
    }

    /** The three place categories, as icons, each toggling its own pins. */
    private fun categoryStrip(): ActionStrip {
        val strip = ActionStrip.Builder()
        PlaceCategory.entries.forEach { category ->
            val selected = poiKind == PoiKind.from(category)
            strip.addAction(
                Action.Builder()
                    .setIcon(
                        icon(
                            category.carIconRes(),
                            if (selected) CarColor.PRIMARY else CarColor.DEFAULT,
                        )
                    )
                    .setOnClickListener { toggle(category) }
                    .build()
            )
        }
        return strip.build()
    }

    private fun contentTemplate(): Template {
        val list = ItemList.Builder()
        if (routing) {
            list.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_route_working))
                    .build()
            )
        }
        nearbyPlaces().forEach { list.addItem(placeRow(it)) }
        return ListTemplate.Builder()
            .setHeader(header())
            .setSingleList(list.build())
            .build()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(title())
        .setStartHeaderAction(Action.BACK)
        .build()

    /** Street name once the offline tiles resolve one, the raw fix otherwise. */
    private fun title(): String {
        currentPlaceRepository.label.value?.let { return it }
        val here = locationRepository.location.value
            ?: return carContext.getString(R.string.car_map_locating)
        return carContext.getString(R.string.car_map_coordinates, here.first, here.second)
    }

    /**
     * A found place, titled with its distance from the car. The title carries
     * a [DistanceSpan] rather than a formatted string so the host renders it
     * in the unit the car is set to.
     */
    private fun placeRow(place: PlacedAt): Row {
        val title = SpannableTitle(place.place.name.ifBlank { carContext.getString(R.string.car_map_place) })
        return Row.Builder()
            .setTitle(title.withDistance(place.metres))
            .setOnClickListener { routeTo(place.place) }
            .build()
    }

    private fun mapAction(iconRes: Int, onClick: () -> Unit): Action = Action.Builder()
        .setIcon(icon(iconRes))
        .setOnClickListener(onClick)
        .build()

    private fun icon(iconRes: Int, tint: CarColor = CarColor.DEFAULT): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes))
            .setTint(tint)
            .build()

    private fun recentre() {
        clearRoute()
        renderer.centreOnDriver()
    }

    private fun toggle(category: PlaceCategory) {
        val kind = PoiKind.from(category)
        if (poiKind == kind) clear() else select(kind)
    }

    private fun select(kind: PoiKind) {
        poiKind = kind
        renderer.showPois(kind)
        invalidate()
    }

    private fun clear() {
        poiKind = null
        renderer.showPois(null)
        invalidate()
    }

    /** Pinned places, nearest first — the order a driver wants to read them in. */
    private fun nearbyPlaces(): List<PlacedAt> {
        val here = locationRepository.location.value ?: return emptyList()
        return renderer.pinnedPlaces()
            .map { PlacedAt(it, metresBetween(here.first, here.second, it.latitude, it.longitude)) }
            .sortedBy { it.metres }
            .take(MAX_PLACES)
    }

    private fun routeTo(place: CarPlace) {
        val from = locationRepository.location.value ?: return
        routeJob?.cancel()
        // Collapsing the list here, not when the route lands: the driver has
        // made their choice, and what they want to see next is the road.
        poiKind = null
        renderer.showPois(null)
        routing = true
        travelEstimate = null
        invalidate()
        routeJob = lifecycleScope.launch {
            val result = routingRepository.route(from, place.latitude to place.longitude)
            routing = false
            if (result == null) {
                renderer.showRoute(emptyList())
                speechSynthesisRepository.speak(carContext.getString(R.string.car_route_failed))
            } else {
                renderer.showRoute(result.points)
                travelEstimate = estimateFor(result.distanceMeters, result.durationSeconds)
                // Same as the phone: the driver hears the result, the screen
                // only has to confirm it.
                speechSynthesisRepository.speak(
                    "${formatDistance(result.distanceMeters)}, " +
                        formatDuration(result.durationSeconds)
                )
            }
            invalidate()
        }
    }

    /**
     * Remaining distance and arrival time in the host's own banner. The host
     * formats both in the car's units and locale, so nothing here is a
     * pre-rendered string.
     */
    private fun estimateFor(metres: Int, seconds: Int): TravelEstimate {
        val arrival = System.currentTimeMillis() + seconds * 1_000L
        return TravelEstimate.Builder(
            Distance.create(metres / 1000.0, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(arrival, TimeZone.getDefault()),
        )
            .setRemainingTimeSeconds(seconds.toLong())
            .build()
    }

    private fun clearRoute() {
        routeJob?.cancel()
        routeJob = null
        routing = false
        travelEstimate = null
        renderer.showRoute(emptyList())
        invalidate()
    }

    private fun formatDistance(metres: Int): String =
        if (metres < 1000) "$metres m" else "%,.1f km".format(metres / 1000.0)

    private fun formatDuration(seconds: Int): String {
        val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        if (minutes < 60) return "$minutes min"
        return "${minutes / 60} h ${minutes % 60} min"
    }

    private fun PlaceCategory.carLabelRes(): Int = when (this) {
        PlaceCategory.FUEL -> R.string.map_poi_fuel
        PlaceCategory.HOTEL -> R.string.map_poi_hotel
        PlaceCategory.FOOD -> R.string.map_poi_food
    }

    private fun PlaceCategory.carIconRes(): Int = when (this) {
        PlaceCategory.FUEL -> R.drawable.ic_gas_station
        PlaceCategory.HOTEL -> R.drawable.ic_hotel
        PlaceCategory.FOOD -> R.drawable.ic_restaurant
    }

    /** A pinned place plus how far the car is from it, in metres. */
    private data class PlacedAt(val place: CarPlace, val metres: Double)

    /** Builds "<name> · <distance>" with the distance as a host-formatted span. */
    private class SpannableTitle(private val name: String) {
        fun withDistance(metres: Double): CarText {
            val text = android.text.SpannableString("$name  ${DISTANCE_PLACEHOLDER}")
            val start = text.length - DISTANCE_PLACEHOLDER.length
            text.setSpan(
                DistanceSpan.create(
                    Distance.create(metres / 1000.0, Distance.UNIT_KILOMETERS)
                ),
                start,
                text.length,
                android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
            )
            return CarText.create(text)
        }

        private companion object {
            /** One char the host replaces with the formatted distance. */
            const val DISTANCE_PLACEHOLDER = " "
        }
    }

    private companion object {
        /** Enough to choose from without turning the list into reading. */
        const val MAX_PLACES = 6

        const val EARTH_RADIUS_M = 6_371_000.0
    }

    private fun metresBetween(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
