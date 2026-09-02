package dev.pgm.roadmate.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.utils.PlaceName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val offlineMap: OfflineMapController,
    private val memoryRepository: MemoryRepository,
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

    /** Fires when a voice search arrives, so the shell can show the map tab. */
    private val _showMap = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showMap: SharedFlow<Unit> = _showMap.asSharedFlow()

    init {
        offlineMap.refresh()
        viewModelScope.launch {
            mapSearchCoordinator.requests.collect(::applyVoiceSearch)
        }
    }

    private fun applyVoiceSearch(request: MapSearchRequest) {
        val kind = request.category?.let(PoiKind::from)
        _poiFilter.value = kind
        _nameQuery.value = if (kind == null) request.rawQuery.trim().ifBlank { null } else null
        _showMap.tryEmit(Unit)
    }

    fun togglePoiFilter(kind: PoiKind) {
        _nameQuery.value = null
        _poiFilter.value = if (_poiFilter.value == kind) null else kind
    }

    fun clearVoiceSearch() {
        _nameQuery.value = null
    }

    fun downloadVisibleRegion(bounds: LatLngBounds, pixelRatio: Float) {
        offlineMap.download(styleUrl, bounds, pixelRatio)
    }

    /** Called when the driver taps a POI to navigate to it — that's a place
     *  they actually went, so it counts toward frequent-place memory. */
    fun recordVisit(placeLabel: String) {
        if (placeLabel.isBlank()) return
        viewModelScope.launch { memoryRepository.rememberPlace(PlaceName.normalize(placeLabel)) }
    }
}
