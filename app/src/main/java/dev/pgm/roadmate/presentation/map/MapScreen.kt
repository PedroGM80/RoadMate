package dev.pgm.roadmate.presentation.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.ui.theme.Spacing
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Point

private const val PIN_PREFIX = "roadmate-pin-"

/** Icon key for a name-search result (no category colour). */
private const val NAME_PIN = "NAME"

/** Tile POI name properties, most specific first. */
private val NAME_PROPS = arrayOf("name:es", "name", "name:latin", "name_int")

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
) {
    val context = LocalContext.current
    val offlineStatus by viewModel.offlineStatus.collectAsState()
    val poiFilter by viewModel.poiFilter.collectAsState()
    val nameQuery by viewModel.nameQuery.collectAsState()

    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    val mapView = rememberMapViewWithLifecycle()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var selectedPoi by remember { mutableStateOf<Pair<String, LatLng>?>(null) }
    var centeredOnUser by remember { mutableStateOf(false) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri(viewModel.styleUrl)) { style ->
                registerPinIcons(style, context)
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
        repeat(20) {
            if (centeredOnUser) return@LaunchedEffect
            if (centerOnUser(map)) {
                centeredOnUser = true
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    LaunchedEffect(poiFilter, nameQuery, symbolManager) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val manager = symbolManager ?: return@LaunchedEffect
        val active = poiFilter != null || nameQuery != null

        // Right after a chip tap / voice search the current tiles' source
        // features aren't queryable yet, so a single pass finds nothing until
        // the map next idles (e.g. after "recenter"). Retry briefly.
        var pins = refreshPois(map, mapView, manager, poiFilter, nameQuery)
        if (active) {
            repeat(6) {
                if (pins.isNotEmpty()) return@repeat
                delay(350)
                pins = refreshPois(map, mapView, manager, poiFilter, nameQuery)
            }
        }

        // Turning a filter/search on: frame the pins so they're actually
        // visible (they sit across the loaded tiles, not just the ~500 m in
        // view).
        if (active && pins.size >= 2) {
            val b = LatLngBounds.Builder().includes(pins).build()
            runCatching {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 120), 400)
            }
        } else if (active && pins.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pins.first(), 15.0), 400)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

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
                    delay(4000)
                    showReady = false
                }
            }
            if (offlineStatus !is OfflineMapStatus.Ready || showReady) {
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
                                pixelRatio = context.resources.displayMetrics.density,
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
                    shadowElevation = 2.dp,
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
                                label = { Text(stringResource(kind.labelRes)) },
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
                    launchNavigation(context, name, latLng)
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
        shadowElevation = 2.dp,
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

/** A `MapView` wired to the current lifecycle, created once. */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
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


@Composable
private fun MapControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
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
            shadowElevation = 2.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.semantics { contentDescription = zoomInLabel },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                HorizontalDivider(modifier = Modifier.width(24.dp))
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.semantics { contentDescription = zoomOutLabel },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
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
private fun refreshPois(
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
        val points = vectorSource
            ?.querySourceFeatures(arrayOf("poi", "poi_label"), null)
            .orEmpty()
            .filter { it.geometry() is Point }

        val iconImage = PIN_PREFIX + (kind?.name ?: NAME_PIN)
        val fallbackLabel = kind?.let { fallbackLabelFor(mapView.context, it) } ?: nameQuery.orEmpty()
        val matches = if (kind != null) {
            points.filter { it.getStringProperty("class") in kind.classes }
        } else {
            points.filter { f ->
                NAME_PROPS.any { prop ->
                    f.getStringProperty(prop)?.let { foldForSearch(it).contains(needle!!) } == true
                }
            }
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
                    .withIconSize(1.2f)
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

private fun fallbackLabelFor(context: Context, kind: PoiKind): String = when (kind) {
    PoiKind.FUEL -> context.getString(R.string.map_poi_fuel_one)
    PoiKind.HOTEL -> context.getString(R.string.map_poi_hotel_one)
    PoiKind.FOOD -> context.getString(R.string.map_poi_food_one)
}

private fun registerPinIcons(style: Style, context: Context) {
    val density = context.resources.displayMetrics.density
    val size = (26f * density).toInt()
    val cx = size / 2f
    val ring = 2.5f * density

    PoiKind.entries.forEach { kind ->
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // soft drop shadow
        paint.color = 0x33000000
        canvas.drawCircle(cx, cx + 1f * density, cx - ring, paint)
        // white ring
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cx, cx - ring, paint)
        // category fill
        paint.color = kind.tint
        canvas.drawCircle(cx, cx, cx - ring * 2.2f, paint)

        style.addImage(PIN_PREFIX + kind.name, bitmap)
    }

    // Neutral pin for name searches ("busca el Mercadona") — no category tint.
    val nameBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(nameBitmap).apply {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        drawCircle(cx, cx + 1f * density, cx - ring, paint)
        paint.color = 0xFFFFFFFF.toInt()
        drawCircle(cx, cx, cx - ring, paint)
        paint.color = 0xFF455A64.toInt()
        drawCircle(cx, cx, cx - ring * 2.2f, paint)
    }
    style.addImage(PIN_PREFIX + NAME_PIN, nameBitmap)
}

private fun launchNavigation(context: Context, name: String, latLng: LatLng) {
    val googleNav = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}"),
    ).setPackage("com.google.android.apps.maps")
    val geoFallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${latLng.latitude},${latLng.longitude}?q=${Uri.encode(name)}"),
    )
    runCatching { context.startActivity(googleNav) }
        .onFailure { runCatching { context.startActivity(geoFallback) } }
}
