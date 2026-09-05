package dev.pgm.roadmate.presentation.map

import android.annotation.SuppressLint
import android.content.Context
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

// Guarded by the `granted` flag the caller passes; lint can't see that.
@SuppressLint("MissingPermission")
internal fun maybeEnableLocation(map: MapLibreMap, style: Style, context: Context, granted: Boolean) {
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
internal fun centerOnUser(map: MapLibreMap): Boolean {
    val last = map.locationComponent
        .takeIf { it.isLocationComponentActivated }?.lastKnownLocation ?: return false
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), DRIVING_ZOOM),
    )
    return true
}

/** Street-level zoom for a moving driver — city-wide (~13) is too far out. */
internal const val DRIVING_ZOOM = 15.5

/** The driver's current position as (lat, lon), or null with no fix. */
@SuppressLint("MissingPermission")
internal fun MapLibreMap.currentLatLon(): Pair<Double, Double>? {
    val last = locationComponent.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
        ?: return null
    return last.latitude to last.longitude
}
