package dev.pgm.roadmate.presentation.map

import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/command surface the map screen needs for offline regions. An
 * interface so [MapViewModel] can be unit-tested without MapLibre.
 */
interface OfflineMapController {
    val status: StateFlow<OfflineMapStatus>
    fun refresh()
    fun download(styleUrl: String, bounds: LatLngBounds, pixelRatio: Float)

    /** Removes every downloaded region. No-op while a download is running. */
    fun deleteAll()
}

/**
 * Wraps MapLibre's [OfflineManager] so [MapScreen] / [MapViewModel] can
 * download the visible area for offline use and observe progress. MapLibre
 * serves tiles from these regions (and its ambient cache) automatically when
 * the device is offline — no wiring needed on the read side.
 *
 * `OfflineManager` is `@UiThread`; every method here must be called from the
 * main thread (they are, via `viewModelScope`).
 */
@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : OfflineMapController {

    private val offlineManager: OfflineManager by lazy {
        OfflineManager.getInstance(context).apply {
            setOfflineMapboxTileCountLimit(TILE_LIMIT)
        }
    }

    private val _status = MutableStateFlow<OfflineMapStatus>(OfflineMapStatus.Unknown)
    override val status: StateFlow<OfflineMapStatus> = _status.asStateFlow()

    /** Keeps observers alive for the duration of a download. */
    private var activeRegion: OfflineRegion? = null

    @UiThread
    override fun refresh() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (_status.value is OfflineMapStatus.Downloading) return
                val count = offlineRegions?.size ?: 0
                _status.value =
                    if (count > 0) OfflineMapStatus.Ready(count) else OfflineMapStatus.Idle
            }

            override fun onError(error: String) {
                Log.w(TAG, "listOfflineRegions: $error")
                if (_status.value !is OfflineMapStatus.Downloading) {
                    _status.value = OfflineMapStatus.Idle
                }
            }
        })
    }

    @UiThread
    override fun download(styleUrl: String, bounds: LatLngBounds, pixelRatio: Float) {
        if (_status.value is OfflineMapStatus.Downloading) return
        _status.value = OfflineMapStatus.Downloading(0f)

        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, MIN_ZOOM, MAX_ZOOM, pixelRatio,
        )
        val metadata = """{"name":"roadmate-${System.currentTimeMillis()}"}""".toByteArray()

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    activeRegion = offlineRegion
                    offlineRegion.setObserver(regionObserver(offlineRegion))
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    Log.w(TAG, "createOfflineRegion: $error")
                    _status.value = OfflineMapStatus.Failed(R.string.map_offline_error_start)
                }
            },
        )
    }

    @UiThread
    override fun deleteAll() {
        if (_status.value is OfflineMapStatus.Downloading) return
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty()
                if (regions.isEmpty()) {
                    _status.value = OfflineMapStatus.Idle
                    return
                }
                // Only claim "nothing downloaded" once the deletes have
                // actually landed — reporting Idle up front made the UI offer
                // "Descargar esta zona" over regions that were still on disk.
                var outstanding = regions.size
                regions.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = settle()
                        override fun onError(error: String) {
                            Log.w(TAG, "region delete: $error")
                            settle()
                        }

                        private fun settle() {
                            if (--outstanding <= 0) refresh()
                        }
                    })
                }
            }

            override fun onError(error: String) {
                Log.w(TAG, "deleteAll list: $error")
            }
        })
    }

    private fun regionObserver(region: OfflineRegion) =
        object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                _status.value = offlineMapProgress(
                    completedResources = status.completedResourceCount,
                    requiredResources = status.requiredResourceCount,
                    isComplete = status.isComplete,
                )
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    activeRegion = null
                    refresh()
                }
            }

            override fun onError(error: OfflineRegionError) {
                Log.w(TAG, "offline region error: ${error.reason} ${error.message}")
                // Reporting the failure isn't enough: an ACTIVE region keeps
                // retrying in the background, so the driver saw "error" while
                // the download quietly carried on burning data. Stop it, and
                // let go of the observer so the next attempt starts clean.
                runCatching { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
                activeRegion = null
                _status.value = OfflineMapStatus.Failed(R.string.map_offline_error_download)
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                runCatching { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
                activeRegion = null
                _status.value =
                    OfflineMapStatus.Failed(R.string.map_offline_error_too_big)
            }
        }

    private companion object {
        const val MIN_ZOOM = 0.0
        const val MAX_ZOOM = 14.0
        const val TILE_LIMIT = 60_000L
        const val TAG = "OfflineMapManager"
    }
}
