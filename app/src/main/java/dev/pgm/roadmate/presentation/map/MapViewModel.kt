package dev.pgm.roadmate.presentation.map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val offlineMap: OfflineMapController,
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
}
