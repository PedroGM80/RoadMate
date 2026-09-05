package dev.pgm.roadmate.presentation.map

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.ui.theme.Dimens
import dev.pgm.roadmate.ui.theme.Elevation
import dev.pgm.roadmate.ui.theme.IconSize
import dev.pgm.roadmate.ui.theme.Spacing
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.pgm.roadmate.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot
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

    // Runs exactly once per MapView, not once per entry into composition:
    // setStyle again on the same map would stack a second set of annotation
    // managers (duplicate layers/sources) and a second camera-idle listener.
    LaunchedEffect(paneState) {
        if (!paneState.claimSetup()) return@LaunchedEffect
        mapView.getMapAsync { map ->
            mapLibreMap = map
            // Lift the MapLibre logo + (i) so they sit above the bottom
            // filter-chip row instead of tucked behind its corner (still
            // visible, as the OSM/MapLibre licence requires).
            val side = (8 * density).toInt()
            val lift = (150 * density).toInt()
            map.uiSettings.setLogoMargins(side, 0, 0, lift)
            map.uiSettings.setAttributionMargins(side, 0, 0, lift)
            map.setStyle(Style.Builder().fromUri(viewModel.styleUrl)) { style ->
                registerPinIcons(style, context, density)
                // Created before the SymbolManager so the route line + its
                // destination dot sit under the pins.
                lineManager = LineManager(mapView, map, style)
                circleManager = CircleManager(mapView, map, style)
                val manager = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                    textAllowOverlap = false
                    addClickListener { symbol ->
                        val name = symbol.data?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                        selectedPoi = name to symbol.latLng
                        true
                    }
                }
                symbolManager = manager
                maybeEnableLocation(map, style, context, locationPermission.status.isGranted)
                // Registered here (not before the style loads) so `manager` is real.
                map.addOnCameraIdleListener {
                    refreshPois(map, mapView, manager, viewModel.poiFilter.value, viewModel.nameQuery.value)
                    resolvePlaceLabel(map, viewModel, geoThrottle)
                }
            }
        }
    }

    LaunchedEffect(locationPermission.status.isGranted, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!locationPermission.status.isGranted) return@LaunchedEffect
        map.style?.let { maybeEnableLocation(map, it, context, true) }

        // The first GPS fix can take a few seconds after the component
        // activates; poll for it instead of centering once and giving up
        // (which left the map on the style's zoomed-out default).
        // Pane-scoped: once RoadMate has centred on the driver, coming back to
        // the map tab must not yank the camera away from where they panned.
        repeat(20) {
            if (paneState.centeredOnUser) return@LaunchedEffect
            if (centerOnUser(map)) {
                paneState.centeredOnUser = true
                return@LaunchedEffect
            }
            delay(500.milliseconds)
        }
    }

    // Reverse-geocode the driver's position from the downloaded tiles (no
    // network geocoder) and publish it for the Voz screen's location chip.
    // The camera-idle listener drives this once the map settles; this loop is
    // the fallback for a stationary driver whose map loads already centred
    // (no camera move -> no idle callback).
    LaunchedEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        repeat(8) {
            delay(3_000.milliseconds)
            resolvePlaceLabel(map, viewModel, geoThrottle)
        }
    }

    LaunchedEffect(poiFilter, nameQuery, navigateToResult, symbolManager) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val manager = symbolManager ?: return@LaunchedEffect
        val active = poiFilter != null || nameQuery != null

        // The selection changed (a chip tap / toggle-off / voice search) —
        // always drop the old pins first. refreshPois keeps them on an empty
        // result only for the camera-idle path (zoom-out past the POI layer).
        runCatching { manager.deleteAll() }
        if (!active) {
            poiLoading = false
            return@LaunchedEffect
        }

        poiLoading = true
        try {
            // Right after a chip tap / voice search the current tiles' source
            // features aren't queryable yet, so a single pass finds nothing
            // until the map next idles (e.g. after "recenter"). Retry briefly.
            var pins = refreshPois(map, mapView, manager, poiFilter, nameQuery)
            repeat(6) {
                if (pins.isNotEmpty()) return@repeat
                delay(350.milliseconds)
                pins = refreshPois(map, mapView, manager, poiFilter, nameQuery)
            }

            // Still nothing: the current zoom has no usable tiles for this
            // query. A category (POIs, min zoom ~14) needs zooming *in*; a
            // place name — likely a town some km away — needs zooming *out*
            // so its "place" tile loads.
            if (pins.isEmpty()) {
                val z = map.cameraPosition.zoom
                val jump = when {
                    poiFilter != null && z < 14.0 -> 14.5
                    poiFilter == null && z > 11.0 -> 10.5
                    else -> null
                }
                if (jump != null) {
                    runCatching { map.animateCamera(CameraUpdateFactory.zoomTo(jump), 300) }
                    repeat(8) {
                        delay(400.milliseconds)
                        pins = refreshPois(map, mapView, manager, poiFilter, nameQuery)
                        if (pins.isNotEmpty()) return@repeat
                    }
                }
            }

            // Nothing at all — tell the driver instead of leaving them hanging.
            if (pins.isEmpty()) {
                if (navigateToResult || nameQuery != null) {
                    val what = nameQuery ?: filterLabel ?: ""
                    viewModel.onSearchFoundNothing(what)
                }
                return@LaunchedEffect
            }

            // "llévame a…": for a category ("una gasolinera") route to the
            // nearest pin; for a name ("Chiclana") refreshPois already put the
            // best match first, so trust that.
            if (navigateToResult) {
                val here = map.currentLatLon()
                val target = if (poiFilter != null && here != null) {
                    pins.minByOrNull { metersBetween(here, it.latitude to it.longitude) } ?: pins.first()
                } else {
                    pins.first()
                }
                viewModel.onNavigationTargetResolved(
                    from = here,
                    to = target.latitude to target.longitude,
                )
                return@LaunchedEffect
            }

            // Frame the pins. Clamp the zoom so a city-wide spread doesn't drop
            // below the POI layer's min zoom — otherwise the *next* category
            // switch queries tiles that aren't loaded and finds nothing.
            if (pins.size >= 2) {
                val b = LatLngBounds.Builder().includes(pins).build()
                runCatching {
                    val cam = map.getCameraForLatLngBounds(b, intArrayOf(120, 120, 120, 120))
                    if (cam != null) {
                        val z = cam.zoom.coerceIn(13.5, 16.5)
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                org.maplibre.android.camera.CameraPosition.Builder(cam).zoom(z).build(),
                            ),
                            400,
                        )
                    }
                }
            } else if (pins.size == 1) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pins.first(), 15.0), 400)
            }
        } finally {
            poiLoading = false
        }
    }

    // Draw (or clear) the offline route line + its destination dot.
    LaunchedEffect(route, lineManager) {
        val manager = lineManager ?: return@LaunchedEffect
        runCatching { manager.deleteAll() }
        runCatching { circleManager?.deleteAll() }
        if (route.size < 2) return@LaunchedEffect
        val pts = route.map { LatLng(it.first, it.second) }
        runCatching {
            manager.create(
                LineOptions()
                    .withLatLngs(pts)
                    .withLineColor("#1565C0")
                    .withLineWidth(5f),
            )
        }
        runCatching {
            circleManager?.create(
                CircleOptions()
                    .withLatLng(pts.last())
                    .withCircleRadius(7f)
                    .withCircleColor("#1565C0")
                    .withCircleStrokeColor("#FFFFFF")
                    .withCircleStrokeWidth(2.5f),
            )
        }
        mapLibreMap?.let { m ->
            runCatching {
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().includes(pts).build(), 140,
                    ),
                    500,
                )
            }
        }
    }

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
