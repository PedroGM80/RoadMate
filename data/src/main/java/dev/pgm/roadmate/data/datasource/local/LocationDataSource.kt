package dev.pgm.roadmate.data.datasource.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.PermissionManager
import dev.pgm.roadmate.domain.model.UserLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Low-level GPS access via the fused location provider. Returns null instead of
 * throwing when location permission hasn't been granted or no fix is available
 * (e.g. GPS-denied environments like tunnels or underground parking).
 */
class LocationDataSource @Inject constructor(@ApplicationContext context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val permissionManager = PermissionManager(context)

    // hasLocationPermission() is the guard; lint's flow analysis doesn't follow
    // it into PermissionManager, hence the suppress rather than an inline check.
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): UserLocation? {
        if (!permissionManager.hasLocationPermission()) return null
        return fetchLocation()
    }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private suspend fun fetchLocation(): UserLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fusedLocationClient.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    continuation.resume(location?.let { 
                        UserLocation(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            speedKmh = if (it.hasSpeed()) (it.speed * 3.6f).roundToInt() else null
                        )
                    })
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
        }
}
