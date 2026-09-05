package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
    offlineMap: OfflineMapController,
) : Screen(carContext) {

    /** Which category is pinned on the map; null means none is expanded. */
    private var poiKind: PoiKind? = null

    /**
     * The active route's estimate, shown by the host in its own banner rather
     * than as a row: with the list hidden there is no list to put it in, and
     * a TravelEstimate is what a navigation host is built to display.
     */
    private var travelEstimate: TravelEstimate? = null

    private var routeJob: Job? = null

    /**
     * A one-line route status kept on screen while a route is being computed or
     * when one couldn't be built. Without it a tap on a place with no GPS fix,
     * or a route the engine can't trace, did nothing visible at all — the
     * screen dropped straight back to the bare map and the only feedback was a
     * spoken line, which is inaudible if the car has taken audio focus. Null
     * once a route succeeds: then the host's own travel-estimate banner says it.
     */
    private var routeStatus: String? = null

    /**
     * Guidance, once a route is chosen. Owned by this screen rather than the
     * session because it is this screen's route: leaving it running behind a
     * screen the driver has backed out of would keep announcing turns for a
     * journey nobody is on.
     */
    private val navigation = CarNavigationController(
        carContext,
        locationRepository,
        speechSynthesisRepository,
        lifecycleScope,
        onChanged = ::invalidate,
    )

    init {
        // The host holds the navigation callback until it is cleared, and a
        // callback pointing at a destroyed screen is what makes it report the
        // app as unresponsive on the next connection.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = navigation.release()
        })
        lifecycleScope.launch {
            locationRepository.currentLocation()
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

        // While guidance is running the instruction card wins over the place
        // list: MapWithContentTemplate has nowhere to put a maneuver, so
        // showing a category would silently drop the driver's turn arrow.
        if (navigation.isNavigating || (poiKind == null && routeStatus == null)) {
            return NavigationTemplate.Builder()
                .setMapActionStrip(mapStrip)
                .setActionStrip(actionStrip())
                .apply {
                    navigation.routingInfo?.let { setNavigationInfo(it) }
                    (navigation.travelEstimate ?: travelEstimate)
                        ?.let { setDestinationTravelEstimate(it) }
                }
                .build()
        }

        return MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate())
            .setMapController(MapController.Builder().setMapActionStrip(mapStrip).build())
            .setActionStrip(actionStrip())
            .build()
    }

    /**
     * Categories normally; a stop control while navigating. Ending a route has
     * to be one press and always in the same corner — a driver who wants out of
     * a route wants out now, not after finding the right screen.
     */
    private fun actionStrip(): ActionStrip = if (navigation.isNavigating) {
        ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(carIcon(R.drawable.lucide_ic_square, CarColor.RED))
                    .setTitle(carContext.getString(R.string.car_route_stop))
                    .setOnClickListener { clearRoute() }
                    .build()
            )
            .build()
    } else {
        categoryStrip()
    }

    /** The three place categories, as icons, each toggling its own pins. */
    private fun categoryStrip(): ActionStrip {
        val strip = ActionStrip.Builder()
        PlaceCategory.entries.forEach { category ->
            val kind = PoiKind.from(category)
            val selected = poiKind == kind
            strip.addAction(
                Action.Builder()
                    .setIcon(
                        carIcon(
                            category.carIconRes(),
                            if (selected) CarColor.PRIMARY else CarColor.createCustom(kind.tint, kind.tint),
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
        routeStatus?.let { status ->
            list.addItem(Row.Builder().setTitle(status).build())
        }
        val places = nearbyPlaces()
        places.forEach { list.addItem(placeRow(it)) }
        // A ListTemplate with an empty list is rejected by the host. A category
        // with nothing pinned nearby (or a status-only panel) still needs one
        // row so the screen renders instead of throwing.
        if (routeStatus == null && places.isEmpty()) {
            list.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_no_places))
                    .addText(carContext.getString(R.string.car_map_needs_offline))
                    .setImage(carIcon(R.drawable.lucide_ic_triangle_alert, CarColor.YELLOW))
                    .build()
            )
        }
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
        val loc = locationRepository.location.value
            ?: return carContext.getString(R.string.car_map_locating)
        return carContext.getString(R.string.car_map_coordinates, loc.latitude, loc.longitude)
    }

    /**
     * A found place, titled with its distance from the car. The title carries
     * a [DistanceSpan] rather than a formatted string so the host renders it
     * in the unit the car is set to.
     */
    private fun placeRow(place: PlacedAt): Row {
        val title = SpannableTitle(place.place.name.ifBlank { carContext.getString(R.string.car_map_place) })
        val kind = place.place.kind
        return Row.Builder()
            .setTitle(title.withDistance(place.metres))
            .setImage(
                carIcon(
                    kind?.iconRes ?: R.drawable.lucide_ic_map_pin,
                    if (kind != null) CarColor.createCustom(kind.tint, kind.tint) else CarColor.DEFAULT
                )
            )
            .setOnClickListener { routeTo(place.place) }
            .build()
    }

    private fun mapAction(iconRes: Int, onClick: () -> Unit): Action = Action.Builder()
        .setIcon(carIcon(iconRes))
        .setOnClickListener(onClick)
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
        routeStatus = null
        renderer.showPois(kind)
        invalidate()
    }

    private fun clear() {
        poiKind = null
        routeStatus = null
        renderer.showPois(null)
        invalidate()
    }

    /** Pinned places, nearest first — the order a driver wants to read them in. */
    private fun nearbyPlaces(): List<PlacedAt> {
        val loc = locationRepository.location.value ?: return emptyList()
        return renderer.pinnedPlaces()
            .map { PlacedAt(it, haversineMetres(loc.latitude, loc.longitude, it.latitude, it.longitude)) }
            .sortedBy { it.metres }
            .take(MAX_PLACES)
    }

    private fun routeTo(place: CarPlace) {
        routeJob?.cancel()
        val fromLoc = locationRepository.location.value
        if (fromLoc == null) {
            // No fix yet — say so and keep the panel up, rather than swallow
            // the tap and leave the driver looking at an unchanged screen.
            routeStatus = carContext.getString(R.string.car_route_no_location)
            speechSynthesisRepository.speak(routeStatus!!)
            invalidate()
            return
        }
        val from = fromLoc.latitude to fromLoc.longitude
        // Collapsing the list here, not when the route lands: the driver has
        // made their choice, and what they want to see next is the road.
        poiKind = null
        renderer.showPois(null)
        routeStatus = carContext.getString(R.string.car_route_working)
        travelEstimate = null
        invalidate()
        routeJob = lifecycleScope.launch {
            val result = routingRepository.route(from, place.latitude to place.longitude)
            if (result == null) {
                routeStatus = carContext.getString(R.string.car_route_failed)
                renderer.showRoute(emptyList())
                speechSynthesisRepository.speak(carContext.getString(R.string.car_route_failed))
            } else {
                routeStatus = null
                renderer.showRoute(result.points)
                travelEstimate = estimateFor(result.distanceMeters, result.durationSeconds)
                val summary = "${formatDistance(result.distanceMeters)}, " +
                    formatDuration(result.durationSeconds)
                // Guidance is what makes this navigation rather than a drawn
                // line. It can be refused — another app may hold the host's
                // navigation slot — and the driver is told which of the two
                // they got, because "ruta trazada" and "te voy guiando" are
                // very different promises to make to someone driving.
                val guided = navigation.start(
                    place.name.ifBlank { carContext.getString(R.string.car_map_place) },
                    place.latitude,
                    place.longitude,
                    result,
                )
                speechSynthesisRepository.speak(
                    if (guided) summary else "$summary. ${carContext.getString(R.string.car_route_no_guidance)}"
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
        routeStatus = null
        travelEstimate = null
        navigation.stop()
        renderer.showRoute(emptyList())
        invalidate()
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
            val text = android.text.SpannableString("$name  $DISTANCE_PLACEHOLDER")
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

    }
}
