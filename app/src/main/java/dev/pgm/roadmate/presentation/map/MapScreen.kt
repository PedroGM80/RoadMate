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

private const val PIN_PREFIX = "roadmate-pin-"

/** Icon key for a name-search result (no category colour). */
private const val NAME_PIN = "NAME"

/** Tile POI name properties, most specific first. */
private val NAME_PROPS = arrayOf("name:es", "name", "name:latin", "name_int")

/** Throttles tile reverse-geocoding to "moved enough, and not too often". */
internal class GeoThrottle {
    var at: Pair<Double, Double>? = null
    var whenMs = 0L
}

/** Lower-case and strip accents so "jesus" matches "Jesús". */
private fun foldForSearch(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()

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
private fun maybeEnableLocation(map: MapLibreMap, style: Style, context: Context, granted: Boolean) {
    if (!granted) return
    runCatching {
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).build(),
            )
        }
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.TRACKING
        component.renderMode = RenderMode.COMPASS
        // TRACKING keeps the camera on the dot but never sets a zoom; without
        // this the map follows the user at whatever far-out zoom it loaded at.
        runCatching { component.zoomWhileTracking(DRIVING_ZOOM) }
    }
}

/** Recenter on the last known position at driving zoom. Returns false if there's no fix yet. */
@SuppressLint("MissingPermission")
private fun centerOnUser(map: MapLibreMap): Boolean {
    val last = map.locationComponent
        .takeIf { it.isLocationComponentActivated }?.lastKnownLocation ?: return false
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), DRIVING_ZOOM),
    )
    return true
}

/** Street-level zoom for a moving driver — city-wide (~13) is too far out. */
private const val DRIVING_ZOOM = 15.5

/** The driver's current position as (lat, lon), or null with no fix. */
@SuppressLint("MissingPermission")
private fun MapLibreMap.currentLatLon(): Pair<Double, Double>? {
    val last = locationComponent.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
        ?: return null
    return last.latitude to last.longitude
}

/** Rough planar distance in metres between two (lat, lon) points. */
private fun metersBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val mx = (b.second - a.second) * cos(Math.toRadians(a.first)) * 111_320.0
    val my = (b.first - a.first) * 110_540.0
    return hypot(mx, my)
}

/** Distance in metres from the origin (0,0) to segment a→b (both in local metres). */
private fun segMeters(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    if (dx == 0.0 && dy == 0.0) return hypot(ax, ay)
    val t = (((-ax) * dx + (-ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

private val ROAD_SRC_LAYERS = arrayOf("transportation_name", "transportation")
private val PLACE_SRC_LAYERS = arrayOf("place")
private val LOCALITY_CLASSES = setOf(
    "city", "town", "village", "suburb", "hamlet", "neighbourhood", "quarter", "locality",
)

/**
 * Resolve the driver's position to "calle · localidad" from the downloaded
 * tiles and publish it for the Voz screen's chip. Throttled: only when the
 * driver has moved ~40 m and at most every 8 s. Called from the camera-idle
 * listener (tiles are parsed by then) and a startup fallback loop.
 */
private fun resolvePlaceLabel(map: MapLibreMap, viewModel: MapViewModel, throttle: GeoThrottle) {
    val here = map.currentLatLon() ?: return
    val now = System.currentTimeMillis()
    val moved = throttle.at?.let { metersBetween(it, here) > 40.0 } ?: true
    if (!moved || now - throttle.whenMs < 8_000L) return
    throttle.at = here
    throttle.whenMs = now
    val label = runCatching { placeFromTiles(map, here.first, here.second) }
        .onFailure { dev.pgm.roadmate.ml.DebugTrace.log("geo: threw ${it.message}") }
        .getOrNull()
    dev.pgm.roadmate.ml.DebugTrace.log("geo: ${here.first},${here.second} -> ${label ?: "null"}")
    viewModel.onPlaceResolved(label)
}

/**
 * Reverse-geocode a coordinate from the loaded offline tiles: the nearest
 * named road within ~130 m and the nearest town/locality label. Reads the
 * vector *source* (like the POI query) so it resolves at driving zoom, and
 * runs entirely on-device — no network geocoder.
 */
internal fun placeFromTiles(map: MapLibreMap, atLat: Double, atLon: Double): String? {
    val src = map.style?.sources?.firstOrNull { it is VectorSource } as? VectorSource ?: return null
    val cosLat = cos(Math.toRadians(atLat))
    fun toM(lat: Double, lon: Double): Pair<Double, Double> =
        (lon - atLon) * cosLat * 111_320.0 to (lat - atLat) * 110_540.0

    var road: String? = null
    var roadM = 130.0
    runCatching { src.querySourceFeatures(ROAD_SRC_LAYERS, null) }
        .getOrDefault(emptyList())
        .forEach { f ->
            val name = f.getStringProperty("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val lines: List<List<Point>> = when (val g = f.geometry()) {
                is LineString -> listOf(g.coordinates())
                is MultiLineString -> g.coordinates()
                else -> return@forEach
            }
            for (line in lines) for (i in 0 until line.size - 1) {
                val (ax, ay) = toM(line[i].latitude(), line[i].longitude())
                val (bx, by) = toM(line[i + 1].latitude(), line[i + 1].longitude())
                val d = segMeters(ax, ay, bx, by)
                if (d < roadM) { roadM = d; road = name }
            }
        }

    var locality: String? = null
    var localityM = Double.MAX_VALUE
    runCatching { src.querySourceFeatures(PLACE_SRC_LAYERS, null) }
        .getOrDefault(emptyList())
        .forEach { f ->
            val cls = f.getStringProperty("class")
            if (cls != null && cls !in LOCALITY_CLASSES) return@forEach
            val p = f.geometry() as? Point ?: return@forEach
            val name = f.getStringProperty("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val (mx, my) = toM(p.latitude(), p.longitude())
            val d = hypot(mx, my)
            if (d < localityM) { localityM = d; locality = name }
        }

    dev.pgm.roadmate.ml.DebugTrace.log(
        "geo: road=${road ?: "-"}(${roadM.toInt()}m) locality=${locality ?: "-"}",
    )
    return listOfNotNull(road, locality).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}


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
internal fun refreshPois(
    map: MapLibreMap,
    mapView: MapView,
    manager: SymbolManager,
    kind: PoiKind?,
    nameQuery: String?,
): List<LatLng> {
    return runCatching {
        val needle = nameQuery?.let(::foldForSearch).takeUnless { it.isNullOrBlank() }
        if (kind == null && needle == null) {
            manager.deleteAll()
            return emptyList()
        }

        val vectorSource = map.style?.sources?.firstOrNull { it is VectorSource } as? VectorSource
        // A name search also looks at the "place" layer (towns, villages,
        // neighbourhoods) — "llévame a Chiclana" is a place, not a POI.
        val layers = if (kind == null) {
            arrayOf("poi", "poi_label", "place")
        } else {
            arrayOf("poi", "poi_label")
        }
        val points = vectorSource
            ?.querySourceFeatures(layers, null)
            .orEmpty()
            .filter { it.geometry() is Point }

        val iconImage = PIN_PREFIX + (kind?.name ?: NAME_PIN)
        val fallbackLabel = kind?.let { fallbackLabelFor(mapView.context, it) } ?: nameQuery.orEmpty()
        val matches = if (kind != null) {
            points.filter { it.getStringProperty("class") in kind.classes }
        } else {
            // Best match first: a place whose name *is* the query beats a shop
            // that merely contains the word ("Chiclana" vs "Bahía de Chiclana").
            points
                .mapNotNull { f -> nameMatchScore(f, needle!!)?.let { f to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        }

        dev.pgm.roadmate.ml.DebugTrace.log(
            "POI ${kind?.name ?: "name:$needle"}: source=${vectorSource?.id} pois=${points.size} " +
                "matching=${matches.size}; classes = " +
                points.mapNotNull { it.getStringProperty("class") }.groupingBy { it }.eachCount(),
        )

        val seen = HashSet<String>()
        val pins = matches.asSequence()
            .mapNotNull { f ->
                val p = f.geometry() as Point
                val at = LatLng(p.latitude(), p.longitude())
                if (!seen.add("${p.latitude()},${p.longitude()}")) return@mapNotNull null
                val name = f.getStringProperty("name")?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: fallbackLabel
                // Icon only on the map — a text field needs a glyph font the
                // OpenFreeMap style may not serve, which silently drops the
                // whole symbol. The name rides along in `data` for the sheet.
                at to SymbolOptions()
                    .withLatLng(at)
                    .withIconImage(iconImage)
                    .withIconSize(1.0f)
                    .withData(com.google.gson.JsonPrimitive(name))
            }
            .take(120)
            .toList()
        // Only swap the pins when this query actually found some — zooming out
        // past the POI vector layer's min zoom (~14) returns nothing, and we
        // don't want that to wipe the pins already on screen.
        if (pins.isNotEmpty()) {
            manager.deleteAll()
            manager.create(pins.map { it.second })
        }
        pins.map { it.first }
    }.getOrDefault(emptyList())
}

/** "place"-layer classes that count as somewhere you'd drive *to*. */
private val PLACE_LAYER_CLASSES = setOf(
    "city", "town", "village", "hamlet", "suburb", "neighbourhood", "quarter",
    "municipality", "isolated_dwelling", "locality",
)

/**
 * How well a tile feature's name matches the spoken query, or null if it
 * doesn't. Exact match ranks highest, then prefix, then a whole-word hit,
 * then a bare substring; a town/village gets a bump over a like-named shop.
 */
private fun nameMatchScore(feature: org.maplibre.geojson.Feature, needle: String): Int? {
    val name = NAME_PROPS.firstNotNullOfOrNull { feature.getStringProperty(it) }
        ?.let(::foldForSearch)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    var score = when {
        name == needle -> 100
        name.startsWith("$needle ") || name.startsWith("$needle,") -> 70
        Regex("\\b${Regex.escape(needle)}\\b").containsMatchIn(name) -> 40
        name.contains(needle) -> 10
        else -> return null
    }
    if (feature.getStringProperty("class") in PLACE_LAYER_CLASSES) score += 25
    return score
}

private fun fallbackLabelFor(context: Context, kind: PoiKind): String = when (kind) {
    PoiKind.FUEL -> context.getString(R.string.map_poi_fuel_one)
    PoiKind.HOTEL -> context.getString(R.string.map_poi_hotel_one)
    PoiKind.FOOD -> context.getString(R.string.map_poi_food_one)
}

/**
 * @param sizeDp how big the pin bitmap is. The phone's default reads well at
 *   arm's length; the car screen is further away and lower-resolution, and a
 *   32dp pin there comes out as a speck among the base style's own icons.
 */
internal fun registerPinIcons(style: Style, context: Context, density: Float, sizeDp: Float = 32f) {
    val size = (sizeDp * density).toInt()
    val cx = size / 2f
    val ring = 2.5f * density

    fun pin(fill: Int, iconRes: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // A stronger drop shadow than the phone's — the car display is further
        // from the eye and often glare-lit.
        paint.color = 0x55000000
        canvas.drawCircle(cx, cx + 1.5f * density, cx - ring, paint)
        paint.color = 0xFFFFFFFF.toInt() // crisp white ring
        canvas.drawCircle(cx, cx, cx - ring, paint)
        paint.color = fill // category disc
        canvas.drawCircle(cx, cx, cx - ring * 2.0f, paint)

        ContextCompat.getDrawable(context, iconRes)?.mutate()?.let { d ->
            d.setTint(0xFFFFFFFF.toInt())
            val pad = (size * 0.25f).toInt()
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(canvas)
        }
        return bitmap
    }

    PoiKind.entries.forEach { kind ->
        style.addImage(PIN_PREFIX + kind.name, pin(kind.tint, kind.iconRes))
    }
    // Neutral pin for name searches ("busca el Mercadona") — no category tint.
    style.addImage(PIN_PREFIX + NAME_PIN, pin(0xFF455A64.toInt(), R.drawable.lucide_ic_map_pin))
}

