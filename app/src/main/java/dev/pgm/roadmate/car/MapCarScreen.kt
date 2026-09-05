package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.PlaceCategory
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.presentation.map.OfflineMapController
import kotlinx.coroutines.launch

/**
 * The map for the car display.
 *
 * The map itself is drawn by the **host**, not by RoadMate. Rendering our own
 * MapLibre surface in the car needs `androidx.car.app.ACCESS_SURFACE`, which
 * the host only grants to apps in the navigation category — RoadMate is a POI
 * app (see the manifest note on the CarAppService category), so the
 * host-drawn [PlaceListMapTemplate] is the template that fits: it puts the
 * driver's own position on the map and takes a list beside it.
 *
 * Where the driver is goes in the title, not in a row: [PlaceListMapTemplate]
 * treats its list as a list of *places* and rejects any non-browsable row
 * without a distance attached ("All non-browsable rows must have a distance
 * span"), which a "you are here" row can never have.
 *
 * Category searches go out through [MapSearchCoordinator], the same channel
 * the voice pipeline uses, so "gasolineras" tapped here and "busca
 * gasolineras" spoken end up in one place. The tiles they are matched against
 * live in the phone's offline map, so the confirmation screen says where the
 * result appears rather than pretending the search happened in the car.
 */
class MapCarScreen(
    carContext: CarContext,
    private val locationRepository: LocationRepository,
    private val currentPlaceRepository: CurrentPlaceRepository,
    private val mapSearchCoordinator: MapSearchCoordinator,
    private val preferences: AssistantPreferencesRepository,
    private val gemini: GeminiRepository,
    private val offlineMap: OfflineMapController,
) : Screen(carContext) {

    private var coordinates: Pair<Double, Double>? = locationRepository.location.value

    init {
        lifecycleScope.launch {
            val fix = locationRepository.getCurrentCoordinates()
            if (fix != coordinates) {
                coordinates = fix
                invalidate()
            }
        }
        // Nothing is wired to the raw location flow: every invalidate costs a
        // template permit with the host, and a screen that redraws on each GPS
        // fix runs out of them and is dropped as unresponsive. The host keeps
        // the driver's own marker live by itself through
        // setCurrentLocationEnabled. The label is a StateFlow, so this only
        // fires when the street name actually changes.
        lifecycleScope.launch {
            currentPlaceRepository.label.collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val here = coordinates

        val items = ItemList.Builder()
            .apply { PlaceCategory.entries.forEach { addItem(categoryRow(it)) } }
            // Settings hang off the map rather than the home screen because
            // MessageTemplate only takes two actions and both are spoken for
            // ("Escuchar" and the way here). Browsable, like the categories,
            // for the same distance-span reason.
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_action))
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(settingsScreen()) }
                    .build()
            )
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle(title())
            .setHeaderAction(Action.BACK)
            .setCurrentLocationEnabled(true)
            .setItemList(items)
            .apply {
                here?.let {
                    setAnchor(Place.Builder(CarLocation.create(it.first, it.second)).build())
                }
            }
            .build()
    }

    /** Street name when the phone's offline map has resolved one, else the fix. */
    private fun title(): String {
        currentPlaceRepository.label.value?.let { return it }
        val here = coordinates ?: return carContext.getString(R.string.car_map_locating)
        return carContext.getString(R.string.car_map_coordinates, here.first, here.second)
    }

    private fun categoryRow(category: PlaceCategory): Row = Row.Builder()
        .setTitle(carContext.getString(category.carLabelRes()))
        .setImage(
            CarIcon.Builder(IconCompat.createWithResource(carContext, category.carIconRes()))
                .setTint(CarColor.DEFAULT)
                .build()
        )
        // Browsable, and it really does browse: the click pushes the result
        // screen. A non-browsable row here would have to carry a DistanceSpan,
        // which a category — as opposed to a specific place — has no distance
        // to put in.
        .setBrowsable(true)
        .setOnClickListener { search(category) }
        .build()

    private fun search(category: PlaceCategory) {
        if (!mapSearchCoordinator.hasOfflineMap()) {
            screenManager.push(resultScreen(R.string.car_map_needs_offline))
            return
        }
        lifecycleScope.launch {
            mapSearchCoordinator.submit(
                MapSearchRequest(
                    rawQuery = carContext.getString(category.carLabelRes()),
                    category = category,
                    origin = coordinates,
                )
            )
            screenManager.push(resultScreen(R.string.car_map_search_sent))
        }
    }

    private fun settingsScreen(): SettingsCarScreen = SettingsCarScreen(
        carContext,
        preferences,
        gemini,
        offlineMap,
        locationRepository,
    )

    private fun resultScreen(messageRes: Int): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template =
            MessageTemplate.Builder(carContext.getString(messageRes))
                .setTitle(carContext.getString(R.string.car_map_title))
                .setHeaderAction(Action.BACK)
                .build()
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
}
