package dev.pgm.roadmate.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.BuildConfig
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
 * RoadMate's own map on the car screen, with its own controls.
 *
 * [MapWithContentTemplate] is the arrangement the phone already has: the map
 * fills the pane, a list sits over it, and the host draws a strip of map
 * controls down the side. The map itself is drawn by [CarMapRenderer] onto
 * the surface the host hands out — which is why this screen only exists in
 * the navigation category, the only one the host will grant
 * `androidx.car.app.ACCESS_SURFACE` to.
 *
 * The map action strip holds what the phone's floating buttons hold: pan,
 * zoom in, zoom out, recentre. [Action.PAN] is not ours to implement — the
 * host puts the screen into pan mode and then feeds the drags back through
 * `SurfaceCallback.onScroll`.
 */
class NavigationCarScreen(
    carContext: CarContext,
    private val locationRepository: LocationRepository,
    private val currentPlaceRepository: CurrentPlaceRepository,
    private val mapSearchCoordinator: MapSearchCoordinator,
    private val preferences: AssistantPreferencesRepository,
    private val gemini: GeminiRepository,
    private val offlineMap: OfflineMapController,
) : Screen(carContext), DefaultLifecycleObserver {

    private val renderer = CarMapRenderer(
        carContext,
        BuildConfig.MAP_STYLE_URL,
        locationRepository,
    )

    private var notice: String? = null

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            locationRepository.getCurrentCoordinates()
            renderer.centreOnDriver(animate = false)
            invalidate()
        }
        lifecycleScope.launch {
            currentPlaceRepository.label.collect { invalidate() }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // The surface belongs to whichever screen last claimed it, so it is
        // claimed on the way in and released on the way out — otherwise the
        // map keeps rendering under the voice screen.
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
    }

    override fun onStop(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    override fun onGetTemplate(): Template = MapWithContentTemplate.Builder()
        .setContentTemplate(contentTemplate())
        .setMapController(
            MapController.Builder()
                .setMapActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.PAN)
                        .addAction(mapAction(R.drawable.lucide_ic_locate_fixed, ::onRecentre))
                        .addAction(mapAction(R.drawable.lucide_ic_zoom_in) { renderer.zoomIn() })
                        .addAction(mapAction(R.drawable.lucide_ic_zoom_out) { renderer.zoomOut() })
                        .build()
                )
                .build()
        )
        .setActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.car_settings_action))
                        .setOnClickListener { screenManager.push(settingsScreen()) }
                        .build()
                )
                .build()
        )
        .build()

    private fun contentTemplate(): Template {
        val list = ItemList.Builder()
        // The outcome of a search goes above the categories rather than
        // replacing them: swapping the whole content template for a message
        // left the driver on a dead end with no way back to the list.
        notice?.let { list.addItem(Row.Builder().setTitle(it).build()) }
        PlaceCategory.entries.forEach { list.addItem(categoryRow(it)) }
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

    private fun categoryRow(category: PlaceCategory): Row = Row.Builder()
        .setTitle(carContext.getString(category.carLabelRes()))
        .setImage(
            CarIcon.Builder(IconCompat.createWithResource(carContext, category.carIconRes()))
                .setTint(CarColor.DEFAULT)
                .build()
        )
        .setOnClickListener { search(category) }
        .build()

    private fun mapAction(iconRes: Int, onClick: () -> Unit): Action = Action.Builder()
        .setIcon(
            CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes))
                .setTint(CarColor.DEFAULT)
                .build()
        )
        .setOnClickListener(onClick)
        .build()

    private fun onRecentre() {
        renderer.centreOnDriver()
    }

    private fun search(category: PlaceCategory) {
        if (!mapSearchCoordinator.hasOfflineMap()) {
            showNotice(R.string.car_map_needs_offline)
            return
        }
        lifecycleScope.launch {
            mapSearchCoordinator.submit(
                MapSearchRequest(
                    rawQuery = carContext.getString(category.carLabelRes()),
                    category = category,
                    origin = locationRepository.location.value,
                )
            )
            showNotice(R.string.car_map_search_sent)
        }
    }

    private fun showNotice(messageRes: Int) {
        notice = carContext.getString(messageRes)
        invalidate()
    }

    private fun settingsScreen(): SettingsCarScreen = SettingsCarScreen(
        carContext,
        preferences,
        gemini,
        offlineMap,
        locationRepository,
    )

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
