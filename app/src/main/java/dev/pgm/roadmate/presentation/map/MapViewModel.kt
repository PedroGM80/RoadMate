package dev.pgm.roadmate.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.utils.PlaceName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val offlineMap: OfflineMapController,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    /** Tile + style source. Keyless OpenFreeMap by default (see app build.gradle.kts). */
    val styleUrl: String = BuildConfig.MAP_STYLE_URL

    val offlineStatus: StateFlow<OfflineMapStatus> = offlineMap.status

    private val _poiFilter = MutableStateFlow<PoiKind?>(null)
    val poiFilter: StateFlow<PoiKind?> = _poiFilter.asStateFlow()

    init {
        offlineMap.refresh()
    }

    fun togglePoiFilter(kind: PoiKind) {
        _poiFilter.value = if (_poiFilter.value == kind) null else kind
    }

    fun downloadVisibleRegion(bounds: LatLngBounds, pixelRatio: Float) {
        offlineMap.download(styleUrl, bounds, pixelRatio)
    }

    /** Called when the driver taps "Ir con Google Maps" on a POI — that's a
     *  place they actually went, so it counts toward frequent-place memory. */
    fun recordVisit(placeLabel: String) {
        if (placeLabel.isBlank()) return
        viewModelScope.launch { memoryRepository.rememberPlace(PlaceName.normalize(placeLabel)) }
    }
}
