package dev.pgm.roadmate.presentation.map

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.pgm.roadmate.ui.theme.Dimens
import dev.pgm.roadmate.ui.theme.Elevation
import dev.pgm.roadmate.ui.theme.IconSize
import dev.pgm.roadmate.ui.theme.Spacing
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.pgm.roadmate.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.SymbolManager
import kotlin.time.Duration.Companion.milliseconds
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
    paneState: MapPaneState = rememberMapPaneState(),
) {
    val context = LocalContext.current
    val offlineStatus by viewModel.offlineStatus.collectAsStateWithLifecycle()
    val poiFilter by viewModel.poiFilter.collectAsStateWithLifecycle()
    val nameQuery by viewModel.nameQuery.collectAsStateWithLifecycle()
    val navigateToResult by viewModel.navigateToResult.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val routeSummary by viewModel.routeSummary.collectAsStateWithLifecycle()

    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val density = LocalDensity.current.density
    val filterLabel = poiFilter?.let { stringResource(it.labelRes) }

    // Everything below that has to outlive a tab switch lives in [paneState]:
    // the MapView itself, the loaded style's annotation managers and the
    // one-shot setup flag. Only genuinely transient UI state (the POI spinner)
    // stays local to this composition.
    val mapView = paneState.mapView
    var mapLibreMap by paneState.map
    var symbolManager by paneState.symbolManager
    var lineManager by paneState.lineManager
    var circleManager by paneState.circleManager
    var selectedPoi by paneState.selectedPoi
    var poiLoading by remember { mutableStateOf(false) }
    val geoThrottle = paneState.geoThrottle

    MapSetupEffect(paneState, viewModel, density, locationPermission.status.isGranted)

    LocationCenteringEffect(mapLibreMap, locationPermission.status.isGranted, paneState)

    PlaceLabelFallbackEffect(mapLibreMap, viewModel, geoThrottle)

    PoiSearchEffect(
        map = mapLibreMap,
        symbolManager = symbolManager,
        mapView = mapView,
        poiFilter = poiFilter,
        nameQuery = nameQuery,
        navigateToResult = navigateToResult,
        filterLabel = filterLabel,
        viewModel = viewModel,
        onLoadingChange = { poiLoading = it },
    )
    RouteLineEffect(route, lineManager, circleManager, mapLibreMap)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            // The MapView outlives this composition, so on re-entry it may
            // still be attached to the slot it was torn out of.
            factory = {
                (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // The map stays full-bleed; only the controls inset past the system bars.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.sm),
        ) {
            // "Listo" is a confirmation, not a permanent state — let it fade
            // after a few seconds. Everything else stays put.
            var showReady by remember { mutableStateOf(true) }
            LaunchedEffect(offlineStatus) {
                if (offlineStatus is OfflineMapStatus.Ready) {
                    showReady = true
                    delay(4000.milliseconds)
                    showReady = false
                }
            }
            if (poiLoading) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = Elevation.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.sm),
                            strokeWidth = Dimens.progressStroke,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            poiFilter?.let { stringResource(R.string.map_poi_loading, stringResource(it.labelRes)) }
                                ?: stringResource(R.string.map_poi_loading_generic),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else if (routeSummary != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = Elevation.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            routeSummary.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        IconButton(onClick = viewModel::clearRoute, modifier = Modifier.size(IconSize.md)) {
                            Icon(
                                painterResource(R.drawable.lucide_ic_square),
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                    }
                }
            } else if (offlineStatus !is OfflineMapStatus.Ready || showReady) {
                OfflineStatusChip(
                    status = offlineStatus,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            MapControls(
                modifier = Modifier.align(Alignment.CenterEnd),
                onZoomIn = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomBy(1.0), 200) },
                onZoomOut = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomBy(-1.0), 200) },
                onRecenter = { mapLibreMap?.let { centerOnUser(it) } },
            )

            // Bottom row: filter chips + (when there's nothing saved yet) the
            // download action, both clear of the app's navigation bar.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (offlineStatus !is OfflineMapStatus.Ready &&
                    offlineStatus !is OfflineMapStatus.Downloading
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            val map = mapLibreMap ?: return@ExtendedFloatingActionButton
                            viewModel.downloadVisibleRegion(
                                bounds = map.projection.visibleRegion.latLngBounds,
                                pixelRatio = density,
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.lucide_ic_download), contentDescription = null) },
                        text = { Text(stringResource(R.string.map_download_area)) },
                        modifier = Modifier.align(Alignment.End),
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = Elevation.low,
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PoiKind.entries.forEach { kind ->
                            FilterChip(
                                selected = poiFilter == kind,
                                onClick = { viewModel.togglePoiFilter(kind) },
                                label = {
                                    Icon(
                                        painterResource(kind.iconRes),
                                        contentDescription = stringResource(kind.labelRes),
                                        modifier = Modifier.size(IconSize.md),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        selectedPoi?.let { (name, latLng) ->
            PoiSheet(
                name = name.ifBlank { stringResource(R.string.map_poi_fallback_name) },
                onNavigate = {
                    viewModel.recordVisit(name)
                    // Offline route via BRouter — no external Maps.
                    viewModel.routeTo(
                        from = mapLibreMap?.currentLatLon(),
                        to = latLng.latitude to latLng.longitude,
                    )
                    selectedPoi = null
                },
                onDismiss = { selectedPoi = null },
            )
        }
    }
}

@Composable
private fun OfflineStatusChip(status: OfflineMapStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        OfflineMapStatus.Unknown -> return
        OfflineMapStatus.Idle -> stringResource(R.string.map_offline_idle)
        is OfflineMapStatus.Downloading ->
            stringResource(R.string.map_offline_downloading, (status.progress * 100).toInt())
        is OfflineMapStatus.Ready -> stringResource(R.string.map_offline_ready)
        is OfflineMapStatus.Failed -> stringResource(status.messageRes)
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = Elevation.low,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md - Spacing.xs, vertical = Spacing.sm)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (status is OfflineMapStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (status is OfflineMapStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { status.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = Spacing.xs).fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiSheet(
    name: String,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.md))
            Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.map_navigate))
            }
        }
    }
}

/**
 * The map pane's long-lived pieces: the [MapView] and everything built
 * against its loaded style.
 *
 * Hoisted out of [MapScreen] on purpose. The shell swaps Voz/Mapa with a
 * Crossfade, so MapScreen leaves composition on every tab switch — and a
 * MapView created inside it was destroyed and rebuilt each time, tearing down
 * the GL context and re-fetching the style. Held here, a tab switch only
 * detaches the view (its surface goes away, so it stops drawing and costs no
 * battery) and re-attaches it on return.
 */
@Stable
class MapPaneState internal constructor(internal val mapView: MapView) {
    internal val map = mutableStateOf<MapLibreMap?>(null)
    internal val symbolManager = mutableStateOf<SymbolManager?>(null)
    internal val lineManager = mutableStateOf<LineManager?>(null)
    internal val circleManager = mutableStateOf<CircleManager?>(null)
    internal val selectedPoi = mutableStateOf<Pair<String, LatLng>?>(null)
    internal val geoThrottle = GeoThrottle()

    /** Set once the camera has been put on the driver's first fix. */
    internal var centeredOnUser = false

    private var setupClaimed = false

    /** True for the first caller only — the style/manager setup is one-shot. */
    internal fun claimSetup(): Boolean {
        if (setupClaimed) return false
        setupClaimed = true
        return true
    }
}

/**
 * Creates the pane state and binds its MapView to the current lifecycle.
 * Call this once, above whatever swaps the map in and out — see
 * [dev.pgm.roadmate.presentation.RootScreen].
 */
@Composable
fun rememberMapPaneState(): MapPaneState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val paneState = remember { MapPaneState(MapView(context)) }
    val mapView = paneState.mapView
    // MapView.onDestroy() tears down the native renderer and is not
    // idempotent — calling it twice (ON_DESTROY *and* onDispose, which both
    // run when the activity finishes) trips MapLibre's native side. One flag,
    // one destroy.
    val destroyed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val destroyOnce = { if (destroyed.compareAndSet(false, true)) mapView.onDestroy() }
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyOnce()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // MapLibre keeps a tile cache and GL resources it can give back on
        // request. RoadMate also holds a multi-hundred-MB language model, so
        // it is exactly the kind of app the system asks to make room — and
        // this was the one component never told about it.
        val trimCallback = object : ComponentCallbacks2 {
            // TRIM_MEMORY_RUNNING_* are deprecated on API 35+ (no longer
            // delivered there); still the right signal on the versions that do.
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    runCatching { mapView.onLowMemory() }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated(
                message = "Deprecated in Java",
                replaceWith = ReplaceWith("onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)")
            )
            override fun onLowMemory() {
                runCatching { mapView.onLowMemory() }
            }
        }
        context.registerComponentCallbacks(trimCallback)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterComponentCallbacks(trimCallback) }
            (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
            destroyOnce()
        }
    }
    return paneState
}

@SuppressLint("MissingPermission")

@Composable
private fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomInLabel = stringResource(R.string.map_zoom_in)
    val zoomOutLabel = stringResource(R.string.map_zoom_out)
    Column(
        modifier = modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = Elevation.low,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.semantics { contentDescription = zoomInLabel },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                HorizontalDivider(modifier = Modifier.width(Dimens.routeDividerWidth))
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.semantics { contentDescription = zoomOutLabel },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = Elevation.low,
        ) {
            IconButton(onClick = onRecenter) {
                Icon(
                    painterResource(R.drawable.lucide_ic_map_pin),
                    contentDescription = stringResource(R.string.map_recenter),
                )
            }
        }
    }
}

/**
 * OpenMapTiles-schema POI features carry `class` + `name`. Query the vector
 * *source* (the "poi" layer of the tiles), not `queryRenderedFeatures` — at
 * driving zoom the style only draws a handful of POI icons, so the rendered
 * query finds almost nothing. Source features come from every loaded tile
 * regardless of what's drawn; we clip to the visible region ourselves.
 */
