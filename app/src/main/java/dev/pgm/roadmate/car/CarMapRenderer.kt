package dev.pgm.roadmate.car

import android.app.Presentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import dev.pgm.roadmate.domain.repository.LocationRepository
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.ln

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
) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null

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
        val screen = Presentation(carContext, display.display).apply {
            setContentView(view)
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
            loaded.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                enableDriverDot(loaded, style)
                applyVisibleArea()
                centreOnDriver(animate = false)
            }
        }
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

    fun zoomIn() = map?.moveCamera(CameraUpdateFactory.zoomBy(1.0))

    fun zoomOut() = map?.moveCamera(CameraUpdateFactory.zoomBy(-1.0))

    /** Re-centres on the last known fix. Called by the map strip's recentre action. */
    fun centreOnDriver(animate: Boolean = true) {
        val here = locationRepository.location.value ?: return
        val target = CameraUpdateFactory.newLatLngZoom(
            LatLng(here.first, here.second),
            DRIVING_ZOOM,
        )
        val loaded = map ?: return
        if (animate && centredOnDriver) loaded.animateCamera(target) else loaded.moveCamera(target)
        centredOnDriver = true
    }

    private fun enableDriverDot(loaded: MapLibreMap, style: Style) {
        // The location component throws without the runtime permission rather
        // than degrading, and the car screen must not die over a missing dot.
        runCatching {
            val component = loaded.locationComponent
            if (!component.isLocationComponentActivated) {
                component.activateLocationComponent(
                    LocationComponentActivationOptions.builder(carContext, style).build()
                )
            }
            component.isLocationComponentEnabled = true
        }.onFailure { Log.w(TAG, "no location component on the car map", it) }
    }

    private fun release() {
        mapView?.let { view ->
            runCatching {
                view.onPause()
                view.onStop()
                view.onDestroy()
            }
        }
        mapView = null
        map = null
        runCatching { presentation?.dismiss() }
        presentation = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        centredOnDriver = false
        visibleArea = null
    }

    private companion object {
        const val TAG = "CarMapRenderer"
        const val VIRTUAL_DISPLAY_NAME = "RoadMateCarMap"
        const val DRIVING_ZOOM = 15.5
        val LN_2 = ln(2.0)
    }
}
