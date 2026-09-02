package dev.pgm.roadmate.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.utils.PlaceName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class MapViewModel @Inject constructor(
    private val offlineMap: OfflineMapController,
    private val memoryRepository: MemoryRepository,
    private val routingRepository: RoutingRepository,
    private val currentPlaceRepository: CurrentPlaceRepository,
    mapSearchCoordinator: MapSearchCoordinator,
) : ViewModel() {

    /** Tile + style source. Keyless OpenFreeMap by default (see app build.gradle.kts). */
    val styleUrl: String = BuildConfig.MAP_STYLE_URL

    val offlineStatus: StateFlow<OfflineMapStatus> = offlineMap.status

    private val _poiFilter = MutableStateFlow<PoiKind?>(null)
    val poiFilter: StateFlow<PoiKind?> = _poiFilter.asStateFlow()

    /** A place name spoken by the driver, to match against the offline tiles. */
    private val _nameQuery = MutableStateFlow<String?>(null)
    val nameQuery: StateFlow<String?> = _nameQuery.asStateFlow()

    /** Set when a voice search wants a route drawn to the first match found. */
    private val _navigateToResult = MutableStateFlow(false)
    val navigateToResult: StateFlow<Boolean> = _navigateToResult.asStateFlow()

    /** The route polyline as (lat, lon), empty when there's no active route. */
    private val _route = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val route: StateFlow<List<Pair<Double, Double>>> = _route.asStateFlow()

    /** "12,3 km · 18 min", or an error line, or null when there's no route. */
    private val _routeSummary = MutableStateFlow<String?>(null)
    val routeSummary: StateFlow<String?> = _routeSummary.asStateFlow()

    val routingDataStatus: StateFlow<RoutingDataStatus> = routingRepository.dataStatus

    /** Fires when a voice search arrives, so the shell can show the map tab. */
    private val _showMap = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showMap: SharedFlow<Unit> = _showMap.asSharedFlow()

    private var routeJob: Job? = null

    init {
        offlineMap.refresh()
        viewModelScope.launch {
            mapSearchCoordinator.requests.collect(::applyVoiceSearch)
        }
    }

    private fun applyVoiceSearch(request: MapSearchRequest) {
        clearRoute()
        _showMap.tryEmit(Unit)

        // Home/work: a coordinate is already resolved — route straight there,
        // no POI search.
        val fixedDestination = request.destination
        if (fixedDestination != null) {
            _poiFilter.value = null
            _nameQuery.value = null
            _navigateToResult.value = false
            routeTo(request.origin, fixedDestination)
            return
        }

        val kind = request.category?.let(PoiKind::from)
        _poiFilter.value = kind
        _nameQuery.value = if (kind == null) request.rawQuery.trim().ifBlank { null } else null
        _navigateToResult.value = request.navigate
    }

    fun togglePoiFilter(kind: PoiKind) {
        _nameQuery.value = null
        _navigateToResult.value = false
        clearRoute()
        _poiFilter.value = if (_poiFilter.value == kind) null else kind
    }

    fun clearVoiceSearch() {
        _nameQuery.value = null
        _navigateToResult.value = false
    }

    /** [MapScreen] resolved the driver's street/locality from the offline tiles. */
    fun onPlaceResolved(label: String?) = currentPlaceRepository.update(label)

    /**
     * Called by [MapScreen] once it has resolved the destination for a
     * "llévame a…" search to the first pin it found. Consumes the flag.
     */
    fun onNavigationTargetResolved(from: Pair<Double, Double>?, to: Pair<Double, Double>) {
        _navigateToResult.value = false
        routeTo(from, to)
    }

    /** Draw an offline route from [from] (current location) to [to]. */
    fun routeTo(from: Pair<Double, Double>?, to: Pair<Double, Double>) {
        routeJob?.cancel()
        if (from == null) {
            _route.value = emptyList()
            _routeSummary.value = "No sé dónde estás ahora mismo."
            return
        }
        _routeSummary.value = "Calculando la ruta…"
        routeJob = viewModelScope.launch {
            // While the engine works, mirror any segment-tile download so the
            // driver isn't staring at "Calculando…" through a ~50 MB fetch.
            val watcher = launch {
                routingRepository.dataStatus.collect { st ->
                    when (st) {
                        is RoutingDataStatus.Downloading ->
                            _routeSummary.value =
                                "Descargando mapa de ruta… ${(st.progress * 100).roundToInt()}%"
                        RoutingDataStatus.WaitingForWifi ->
                            _routeSummary.value = "Necesito Wi-Fi para el mapa de ruta de esta zona."
                        else -> Unit
                    }
                }
            }
            val result = routingRepository.route(from, to)
            watcher.cancel()
            if (result == null) {
                _route.value = emptyList()
                _routeSummary.value = when (routingRepository.dataStatus.value) {
                    RoutingDataStatus.WaitingForWifi ->
                        "Conéctate a Wi-Fi para descargar el mapa de ruta de esta zona."
                    is RoutingDataStatus.Failed -> "No pude descargar el mapa de ruta."
                    else -> "No puedo trazar la ruta con el mapa descargado."
                }
            } else {
                _route.value = result.points
                _routeSummary.value = summarize(result.distanceMeters, result.durationSeconds)
            }
        }
    }

    fun clearRoute() {
        routeJob?.cancel()
        routeJob = null
        _route.value = emptyList()
        _routeSummary.value = null
    }

    fun downloadVisibleRegion(bounds: LatLngBounds, pixelRatio: Float) {
        offlineMap.download(styleUrl, bounds, pixelRatio)
    }

    /** Called when the driver picks a POI to go to — a place they actually
     *  went, so it counts toward frequent-place memory. */
    fun recordVisit(placeLabel: String) {
        if (placeLabel.isBlank()) return
        viewModelScope.launch { memoryRepository.rememberPlace(PlaceName.normalize(placeLabel)) }
    }

    private fun summarize(meters: Int, seconds: Int): String {
        val km = meters / 1000.0
        val dist = if (km < 20) "%.1f km".format(SPANISH, km) else "${km.roundToInt()} km"
        val mins = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        val time = if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"
        return "$dist · $time"
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    }
}
