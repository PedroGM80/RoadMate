package dev.pgm.roadmate.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import kotlin.time.Duration.Companion.milliseconds

/**
 * The side-effecting parts of [MapScreen], one composable per concern so the
 * screen body stays a list of what runs rather than 200 lines of how.
 */

/**
 * Reverse-geocodes the driver's position from the downloaded tiles and
 * publishes it for the Voz screen's location chip. The camera-idle listener
 * drives this once the map settles; this loop is the fallback for a
 * stationary driver whose map loads already centred (no camera move -> no
 * idle callback).
 */
@Composable
internal fun PlaceLabelFallbackEffect(
    map: MapLibreMap?,
    viewModel: MapViewModel,
    geoThrottle: GeoThrottle,
) {
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        repeat(8) {
            delay(3_000.milliseconds)
            resolvePlaceLabel(m, viewModel, geoThrottle)
        }
    }
}

/** Draws (or clears) the offline route line and its destination dot. */
@Composable
internal fun RouteLineEffect(
    route: List<Pair<Double, Double>>,
    lineManager: LineManager?,
    circleManager: CircleManager?,
    map: MapLibreMap?,
) {
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
        map?.let { m ->
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
}

/** One-shot: load the style and build the annotation managers, once per MapView. */
@Composable
internal fun MapSetupEffect(
    paneState: MapPaneState,
    viewModel: MapViewModel,
    density: Float,
    isLocationGranted: Boolean,
) {
    val context = LocalContext.current
    LaunchedEffect(paneState) {
        val mapView = paneState.mapView
        var mapLibreMap by paneState.map
        var symbolManager by paneState.symbolManager
        var lineManager by paneState.lineManager
        var circleManager by paneState.circleManager
        var selectedPoi by paneState.selectedPoi
        val geoThrottle = paneState.geoThrottle
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
                maybeEnableLocation(map, style, context, isLocationGranted)
                // Registered here (not before the style loads) so `manager` is real.
                map.addOnCameraIdleListener {
                    refreshPois(map, mapView, manager, viewModel.poiFilter.value, viewModel.nameQuery.value)
                    resolvePlaceLabel(map, viewModel, geoThrottle)
                }
            }
        }
    }
}

/** Turn on the location dot and poll for the first fix to centre on. */
@Composable
internal fun LocationCenteringEffect(
    map: MapLibreMap?,
    isLocationGranted: Boolean,
    paneState: MapPaneState,
) {
    val context = LocalContext.current
    LaunchedEffect(isLocationGranted, map) {
        val map = map ?: return@LaunchedEffect
        if (!isLocationGranted) return@LaunchedEffect
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
}

/**
 * Runs a POI / place search when the filter, the name query or the navigate
 * flag changes: drops the old pins, retries while the tiles settle, nudges
 * the zoom, then frames the pins, routes to one, or reports nothing found.
 */
@Composable
internal fun PoiSearchEffect(
    map: MapLibreMap?,
    symbolManager: SymbolManager?,
    mapView: MapView,
    poiFilter: PoiKind?,
    nameQuery: String?,
    navigateToResult: Boolean,
    filterLabel: String?,
    viewModel: MapViewModel,
    onLoadingChange: (Boolean) -> Unit,
) {
    LaunchedEffect(poiFilter, nameQuery, navigateToResult, symbolManager) {
        val map = map ?: return@LaunchedEffect
        val manager = symbolManager ?: return@LaunchedEffect
        val active = poiFilter != null || nameQuery != null

        // The selection changed (a chip tap / toggle-off / voice search) —
        // always drop the old pins first. refreshPois keeps them on an empty
        // result only for the camera-idle path (zoom-out past the POI layer).
        runCatching { manager.deleteAll() }
        if (!active) {
            onLoadingChange(false)
            return@LaunchedEffect
        }

        onLoadingChange(true)
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
            onLoadingChange(false)
        }
    }
}
