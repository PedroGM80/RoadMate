package dev.pgm.roadmate.presentation.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.geojson.Point

private const val PIN_PREFIX = "roadmate-pin-"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val offlineStatus by viewModel.offlineStatus.collectAsState()
    val poiFilter by viewModel.poiFilter.collectAsState()

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
                        selectedPoi = symbol.textField.orEmpty() to symbol.latLng
                        true
                    }
                }
                symbolManager = manager
                maybeEnableLocation(map, style, context, locationPermission.status.isGranted)
                // Registered here (not before the style loads) so `manager` is real.
                map.addOnCameraIdleListener {
                    refreshPois(map, mapView, manager, viewModel.poiFilter.value)
                }
            }
        }
    }

    LaunchedEffect(locationPermission.status.isGranted, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (locationPermission.status.isGranted) {
            map.style?.let { maybeEnableLocation(map, it, context, true) }
            if (!centeredOnUser) {
                @SuppressLint("MissingPermission")
                val last = map.locationComponent.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
                if (last != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 13.0),
                    )
                    centeredOnUser = true
                }
            }
        }
    }

    LaunchedEffect(poiFilter, symbolManager) {
        mapLibreMap?.let { map -> symbolManager?.let { refreshPois(map, mapView, it, poiFilter) } }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        OfflineStatusChip(
            status = offlineStatus,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PoiKind.entries.forEach { kind ->
                FilterChip(
                    selected = poiFilter == kind,
                    onClick = { viewModel.togglePoiFilter(kind) },
                    label = { Text(kind.label) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                val map = mapLibreMap ?: return@ExtendedFloatingActionButton
                viewModel.downloadVisibleRegion(
                    bounds = map.projection.visibleRegion.latLngBounds,
                    pixelRatio = context.resources.displayMetrics.density,
                )
            },
            icon = { Icon(painterResource(R.drawable.lucide_ic_download), contentDescription = null) },
            text = { Text("Descargar esta zona") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 20.dp),
        )

        selectedPoi?.let { (name, latLng) ->
            PoiSheet(
                name = name.ifBlank { "Sitio" },
                onNavigate = {
                    launchNavigation(context, name, latLng)
                    selectedPoi = null
                },
                onDismiss = { selectedPoi = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun OfflineStatusChip(status: OfflineMapStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        OfflineMapStatus.Unknown -> return
        OfflineMapStatus.Idle -> "Mapa online · pulsa “Descargar esta zona” para usarlo sin conexión"
        is OfflineMapStatus.Downloading -> "Descargando mapa offline... ${(status.progress * 100).toInt()} %"
        is OfflineMapStatus.Ready -> "Mapa offline listo"
        is OfflineMapStatus.Failed -> status.message
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
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
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PoiSheet(
    name: String,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNavigate) { Text("Ir con Google Maps") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
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
    }
}

/** OpenMapTiles-schema POI features carry `class` + `name`; pin the matches. */
private fun refreshPois(map: MapLibreMap, mapView: MapView, manager: SymbolManager, kind: PoiKind?) {
    runCatching {
        manager.deleteAll()
        if (kind == null) return
        val features = map.queryRenderedFeatures(
            RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
        )
        val seen = HashSet<String>()
        val options = features.asSequence()
            .filter { it.geometry() is Point }
            .filter { it.getStringProperty("class") in kind.classes }
            .mapNotNull { f ->
                val name = f.getStringProperty("name")?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val p = f.geometry() as Point
                if (!seen.add("$name@${p.latitude()},${p.longitude()}")) return@mapNotNull null
                SymbolOptions()
                    .withLatLng(LatLng(p.latitude(), p.longitude()))
                    .withIconImage(PIN_PREFIX + kind.name)
                    .withIconSize(1.0f)
                    .withTextField(name)
                    .withTextSize(11f)
                    .withTextOffset(arrayOf(0f, 1.4f))
            }
            .take(80)
            .toList()
        if (options.isNotEmpty()) manager.create(options)
    }
}

private fun registerPinIcons(style: Style, context: Context) {
    val base = ContextCompat.getDrawable(context, R.drawable.ic_map_pin) ?: return
    PoiKind.entries.forEach { kind ->
        val drawable = base.constantState?.newDrawable()?.mutate() ?: return@forEach
        drawable.setTint(kind.tint)
        val w = drawable.intrinsicWidth.coerceAtLeast(48)
        val h = drawable.intrinsicHeight.coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        style.addImage(PIN_PREFIX + kind.name, bitmap)
    }
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
