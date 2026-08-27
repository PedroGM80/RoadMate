package dev.pgm.roadmate.data.datasource.local

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.PermissionManager
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Low-level GPS access via the fused location provider. Returns null instead of
 * throwing when location permission hasn't been granted or no fix is available
 * (e.g. GPS-denied environments like tunnels or underground parking).
 */
class LocationDataSource @Inject constructor(@ApplicationContext context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val permissionManager = PermissionManager(context)

    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        if (!permissionManager.hasLocationPermission()) return null
        return fetchLocation()
    }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private suspend fun fetchLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fusedLocationClient.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    continuation.resume(location?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
        }
}
