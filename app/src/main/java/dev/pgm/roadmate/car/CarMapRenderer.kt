package dev.pgm.roadmate.car

import android.Manifest
import android.annotation.SuppressLint
import android.app.Presentation
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.core.content.ContextCompat.checkSelfPermission
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.presentation.map.PoiKind
import dev.pgm.roadmate.presentation.map.placeFromTiles
import dev.pgm.roadmate.presentation.map.refreshPois
import dev.pgm.roadmate.presentation.map.registerPinIcons
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Draws RoadMate's own MapLibre map onto the car screen.
 *
 * Android Auto hands a templated app a bare [android.view.Surface], not a
 * view hierarchy, and MapLibre only knows how to render into a `MapView`.
 * The bridge is a private [VirtualDisplay] backed by that surface with a
 * [Presentation] on top of it: the map is a normal Android view inside a
 * normal window, and everything it draws lands on the car's surface. That is
 * also why the map runs in texture mode — a SurfaceView's own buffer does not
 * composite onto a virtual display, and the result is a black rectangle.
 *
 * The host owns the gestures. It reports them through [SurfaceCallback] as
 * scroll/scale/fling deltas rather than touch events, so they are applied to
 * the camera by hand; the map's own gesture detectors never see anything,
 * which is why they are all turned off.
 *
 * Requires `androidx.car.app.ACCESS_SURFACE`, and the host only grants that
 * to apps in the navigation category — see the CarAppService's intent filter.
 */
class CarMapRenderer(
    private val carContext: CarContext,
    private val styleUrl: String,
    private val locationRepository: LocationRepository,
    private val currentPlaceRepository: CurrentPlaceRepository,
    private val scope: CoroutineScope,
) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var symbolManager: SymbolManager? = null
    private var lineManager: LineManager? = null
    private var circleManager: CircleManager? = null

    /** The category currently pinned, re-queried on every camera idle. */
    private var poiKind: PoiKind? = null

    /** The route currently drawn, kept so a day/night restyle can redraw it. */
    private var activeRoute: List<Pair<Double, Double>> = emptyList()

    /** Which mode the loaded style is for, so [refreshDayNight] can skip a no-op. */
    private var appliedNight = false

    /** Set once the first camera move has happened, so it only auto-centres once. */
    private var centredOnDriver = false

    /**
     * The slice of the surface the host is not covering with the content pane
     * and the action strips. Kept because [onVisibleAreaChanged] arrives
     * before the map finishes loading, and a padding applied to a null map is
     * silently lost — which is what put the driver's own dot underneath the
     * pane.
     */
    private var visibleArea: Rect? = null

    private var speedView: TextView? = null

    private var lastGeocodedAt: Pair<Double, Double>? = null
    private var lastGeocodedMs = 0L

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        release()

        val displayManager = carContext.getSystemService(DisplayManager::class.java) ?: return
        val display = displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            surfaceContainer.width.coerceAtLeast(1),
            surfaceContainer.height.coerceAtLeast(1),
            surfaceContainer.dpi.coerceAtLeast(1),
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        ) ?: return
        virtualDisplay = display

        val options = MapLibreMapOptions.createFromAttributes(carContext)
            // See the class comment: SurfaceView does not composite here.
            .textureMode(true)
            .attributionEnabled(false)
            .logoEnabled(false)
            .compassEnabled(false)
            // Every gesture arrives through SurfaceCallback instead.
            .scrollGesturesEnabled(false)
            .zoomGesturesEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)

        val view = MapView(carContext, options)
        val root = FrameLayout(carContext).apply {
            addView(view)
            val speed = TextView(carContext).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(24, 12, 24, 12)
                background = GradientDrawable().apply {
                    setColor(0xAA000000.toInt())
                    cornerRadius = 16f
                }
                visibility = View.GONE
                gravity = Gravity.CENTER
            }
            addView(speed, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                setMargins(32, 0, 0, 32)
            })
            speedView = speed
        }

        val screen = Presentation(carContext, display.display).apply {
            setContentView(root)
        }

        try {
            screen.show()
        } catch (t: Throwable) {
            Log.w(TAG, "could not present the car map", t)
            release()
            return
        }

        presentation = screen
        mapView = view

        scope.launch {
            locationRepository.location.collect { loc ->
                val speedKmh = loc?.speedKmh
                speedView?.apply {
                    if (speedKmh != null && speedKmh > 3) {
                        val text = SpannableString("$speedKmh\nkm/h")
                        val numLen = speedKmh.toString().length
                        text.setSpan(RelativeSizeSpan(1.5f), 0, numLen, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        text.setSpan(StyleSpan(Typeface.BOLD), 0, numLen, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        text.setSpan(RelativeSizeSpan(0.6f), numLen, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        
                        this.text = text
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }
            }
        }

        view.onCreate(null)
        view.onStart()
        view.onResume()
        view.getMapAsync { loaded ->
            map = loaded
            loaded.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
                isCompassEnabled = false
            }
            loaded.addOnCameraIdleListener {
                refreshPins()
                resolveWhereWeAre(loaded)
            }
            installStyle(loaded, recentre = true)
        }
    }

    /**
     * Loads the day or night style and rebuilds everything anchored to it:
     * the pin images, the three annotation managers (route line, destination
     * dot, POI pins), and the driver dot. Also, re-applied when the car flips
     * between day and night — see [refreshDayNight].
     */
    private fun installStyle(loaded: MapLibreMap, recentre: Boolean) {
        val view = mapView ?: return
        appliedNight = carContext.isDarkMode
        // A light street style is a headlight in the face at night; the car
        // tells us which mode it is in, so load the matching one.
        loaded.setStyle(Style.Builder().fromUri(activeStyleUrl())) { style ->
            symbolManager?.let { runCatching { it.onDestroy() } }
            lineManager?.let { runCatching { it.onDestroy() } }
            circleManager?.let { runCatching { it.onDestroy() } }

            registerPinIcons(style, carContext, CAR_PIN_DP)
            // Created before the SymbolManager so the route line and its
            // destination dot sit underneath the POI pins, same order as the
            // phone's map.
            lineManager = LineManager(view, loaded, style)
            circleManager = CircleManager(view, loaded, style)
            symbolManager = SymbolManager(view, loaded, style).apply {
                // Both, not just allow-overlap: the base style is dense with
                // its own POI symbols, and without ignore-placement ours lose
                // every collision against them and are dropped silently —
                // created, counted, never drawn.
                iconAllowOverlap = true
                iconIgnorePlacement = true
                textAllowOverlap = true
                textIgnorePlacement = true
            }
            enableDriverDot(loaded, style)
            applyVisibleArea()
            if (recentre) centreOnDriver(animate = false)
            // Put back what was on the old style.
            if (activeRoute.size >= 2) drawRoute(activeRoute)
            refreshPins()
        }
    }

    /**
     * Re-themes the map when the car switches between day and night. Hosts
     * that recreate the surface on that change hit [onSurfaceAvailable]
     * instead; this covers the ones that don't. No-op if the style already
     * matches the current mode.
     */
    fun refreshDayNight() {
        val loaded = map ?: return
        if (carContext.isDarkMode == appliedNight) return
        installStyle(loaded, recentre = false)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = release()

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        // Only the first time: recentring on every layout change would yank
        // the map out from under a driver who has just panned it.
        if (applyVisibleArea() && !centredOnDriver) centreOnDriver(animate = false)
    }

    /**
     * Pushes the last known visible area into the map as camera padding, so
     * "centre on the driver" puts the dot in the middle of what can actually
     * be seen rather than the middle of the surface — half of which is under
     * the content pane.
     *
     * @return true when it was applied; false while the map is still loading.
     */
    // MapLibreMap.setPadding(l,t,r,b) is deprecated with no replacement in the
    // pinned SDK; the int-array overload doesn't exist here yet.
    @Suppress("DEPRECATION")
    private fun applyVisibleArea(): Boolean {
        val area = visibleArea ?: return false
        val view = mapView ?: return false
        val loaded = map ?: return false
        loaded.setPadding(
            area.left,
            area.top,
            (view.width - area.right).coerceAtLeast(0),
            (view.height - area.bottom).coerceAtLeast(0),
        )
        return true
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        // The host reports how far the finger moved; the map scrolls the other
        // way, the same as dragging the map itself.
        map?.scrollBy(-distanceX, -distanceY)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        // Pinch factor is linear, zoom levels are logarithmic: doubling the
        // pinch is exactly one zoom level.
        map?.moveCamera(CameraUpdateFactory.zoomBy(ln(scaleFactor.toDouble()) / LN_2))
    }

    /**
     * Pins one category on the car map, or clears them with null.
     *
     * Same query the phone runs: the POI classes come out of the vector
     * *source* rather than what the style happens to draw, because at driving
     * zoom the style renders only a handful of icons and a rendered query
     * finds almost nothing.
     */
    fun showPois(kind: PoiKind?) {
        poiKind = kind
        if (kind == null) symbolManager?.deleteAll()
        refreshPins()
    }

    private var lastPinCount = 0

    private fun refreshPins() {
        val loaded = map ?: return
        val view = mapView ?: return
        val manager = symbolManager ?: return
        lastPinCount = refreshPois(loaded, view, manager, poiKind, null).size
        Log.i(TAG, "pins for ${poiKind?.name}: $lastPinCount, symbols=${manager.annotations.size()}")
    }

    /**
     * What is pinned right now, read back off the symbols rather than kept in
     * a second list: the pins are recomputed on every camera idle, and a
     * cached copy would drift from what the driver can actually see.
     */
    fun pinnedPlaces(): List<CarPlace> {
        val annotations = symbolManager?.annotations ?: return emptyList()
        return (0 until annotations.size()).mapNotNull { index ->
            val symbol = annotations.valueAt(index) ?: return@mapNotNull null
            val at = symbol.latLng
            val name = symbol.data?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            CarPlace(name, at.latitude, at.longitude, poiKind)
        }
    }

    /** Draws a computed route and frames it. Empty clears whatever is drawn. */
    fun showRoute(points: List<Pair<Double, Double>>) {
        runCatching { lineManager?.deleteAll() }
        runCatching { circleManager?.deleteAll() }
        activeRoute = if (points.size >= 2) points else emptyList()
        if (points.size < 2) return
        drawRoute(points)
    }

    /** The draw half of [showRoute], re-callable after a day/night restyle. */
    private fun drawRoute(points: List<Pair<Double, Double>>) {
        val line = points.map { LatLng(it.first, it.second) }
        val routeColor = routeColor()
        val casingColor = if (carContext.isDarkMode) "#0B1220" else "#FFFFFF"
        runCatching {
            // A casing line underneath so the route reads against water, parks
            // and, at night, the near-black background.
            lineManager?.create(
                LineOptions()
                    .withLatLngs(line)
                    .withLineColor(casingColor)
                    .withLineWidth(ROUTE_WIDTH + 3f)
            )
            lineManager?.create(
                LineOptions()
                    .withLatLngs(line)
                    .withLineColor(routeColor)
                    .withLineWidth(ROUTE_WIDTH)
            )
        }
        runCatching {
            circleManager?.create(
                CircleOptions()
                    .withLatLng(line.last())
                    .withCircleRadius(8f)
                    .withCircleColor(routeColor)
                    .withCircleStrokeColor(casingColor)
                    .withCircleStrokeWidth(3f)
            )
        }
        runCatching {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(line).build(),
                    ROUTE_PADDING_PX,
                )
            )
        }
        // The driver has been taken somewhere on purpose; do not yank the
        // camera back to the car on the next layout pass.
        centredOnDriver = true
    }

    fun zoomIn() = map?.moveCamera(CameraUpdateFactory.zoomBy(1.0))

    fun zoomOut() = map?.moveCamera(CameraUpdateFactory.zoomBy(-1.0))

    /** Re-centres on the last known fix. Called by the map strip's recentre action. */
    fun centreOnDriver(animate: Boolean = true) {
        val loc = locationRepository.location.value ?: return
        val target = CameraUpdateFactory.newLatLngZoom(
            LatLng(loc.latitude, loc.longitude),
            DRIVING_ZOOM,
        )
        val loaded = map ?: return
        if (animate && centredOnDriver) loaded.animateCamera(target) else loaded.moveCamera(target)
        centredOnDriver = true
    }


    private fun resolveWhereWeAre(loaded: MapLibreMap) {
        val loc = locationRepository.location.value ?: return
        val here = loc.latitude to loc.longitude
        val now = System.currentTimeMillis()
        val movedEnough = lastGeocodedAt?.let { metresBetween(it, here) > GEOCODE_MOVE_M } ?: true
        if (!movedEnough || now - lastGeocodedMs < GEOCODE_INTERVAL_MS) return
        lastGeocodedAt = here
        lastGeocodedMs = now
        val label = runCatching { placeFromTiles(loaded, here.first, here.second) }.getOrNull()
        if (label != null) currentPlaceRepository.update(label)
    }

    private fun metresBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = (b.first - a.first) * 110_540.0
        val dLon = (b.second - a.second) * 111_320.0 * cos(Math.toRadians(a.first))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    // The guard is hasLocationPermission() one line in; lint's flow analysis
    // doesn't follow it into a helper, and the whole body is wrapped in
    // runCatching { }.onFailure anyway.
    @SuppressLint("MissingPermission")
    private fun enableDriverDot(loaded: MapLibreMap, style: Style) {
        // Needs a location permission; without one the component throws rather
        // than degrading, and the car screen must not die over a missing dot.
        // The permission is part of RoadMate's normal cascade — this is just
        // the belt-and-braces check the location APIs expect at the call site.
        if (!hasLocationPermission()) return
        runCatching {
            val component = loaded.locationComponent
            if (!component.isLocationComponentActivated) {
                component.activateLocationComponent(
                    LocationComponentActivationOptions.builder(carContext, style)
                        .locationComponentOptions(
                            // RoadMate blue, with a slow pulse so the driver's
                            // own position stays findable at a glance among the
                            // route line and the POI pins.
                            LocationComponentOptions.builder(carContext)
                                .foregroundTintColor(DRIVER_DOT_COLOR)
                                .bearingTintColor(DRIVER_DOT_COLOR)
                                .accuracyColor(DRIVER_DOT_COLOR)
                                .accuracyAlpha(0.12f)
                                .pulseEnabled(true)
                                .pulseColor(DRIVER_DOT_COLOR)
                                .pulseSingleDuration(2_400f)
                                .build()
                        )
                        .build()
                )
            }
            component.isLocationComponentEnabled = true
            // A directional puck that points where the car is heading. The
            // camera stays under this renderer's control (centreOnDriver, route
            // framing, manual pan), so the component itself must not drive it.
            component.cameraMode = CameraMode.NONE
            component.renderMode = RenderMode.GPS
        }.onFailure { Log.w(TAG, "no location component on the car map", it) }
    }

    /** The dark style at night, the configured one otherwise. */
    private fun activeStyleUrl(): String {
        if (!carContext.isDarkMode) return styleUrl
        // OpenFreeMap ships a matching dark style at .../styles/dark; for a
        // self-hosted / MapTiler URL we can't guess one, so keep it.
        return OPENFREEMAP_STYLE.find(styleUrl)
            ?.let { "${it.groupValues[1]}/dark" }
            ?: styleUrl
    }

    private fun routeColor(): String =
        if (carContext.isDarkMode) ROUTE_COLOR_NIGHT else ROUTE_COLOR

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(carContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun release() {
        mapView?.let { view ->
            runCatching {
                view.onPause()
                view.onStop()
                view.onDestroy()
            }
        }
        symbolManager?.let { runCatching { it.onDestroy() } }
        symbolManager = null
        lineManager?.let { runCatching { it.onDestroy() } }
        lineManager = null
        circleManager?.let { runCatching { it.onDestroy() } }
        circleManager = null
        mapView = null
        map = null
        runCatching { presentation?.dismiss() }
        presentation = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        centredOnDriver = false
        appliedNight = false
        visibleArea = null
    }

    private companion object {
        const val TAG = "CarMapRenderer"
        const val VIRTUAL_DISPLAY_NAME = "RoadMateCarMap"
        const val DRIVING_ZOOM = 15.5

        /** RoadMate blue for the route, and a lighter blue that survives the dark style. */
        const val ROUTE_COLOR = "#1565C0"
        const val ROUTE_COLOR_NIGHT = "#5B9CFF"
        const val ROUTE_WIDTH = 5f
        const val ROUTE_PADDING_PX = 140

        /** The driver's own position marker — RoadMate blue in both modes. */
        const val DRIVER_DOT_COLOR = 0xFF1A73E8.toInt()

        /** `https://tiles.openfreemap.org/styles/liberty` → group 1 is everything up to the style name. */
        val OPENFREEMAP_STYLE = Regex("^(https?://tiles\\.openfreemap\\.org/styles)/[a-z0-9-]+/?$")

        /** Don't re-geocode until the car has actually gone somewhere. */
        const val GEOCODE_MOVE_M = 40.0
        const val GEOCODE_INTERVAL_MS = 8_000L

        /**
         * Pin size for the car screen. The phone's 32dp is a speck here: the
         * display is further from the driver and lower-resolution, and the
         * base style's own POI icons are already competing for the same
         * pixels.
         */
        const val CAR_PIN_DP = 72f
        val LN_2 = ln(2.0)
    }
}
