package dev.pgm.roadmate.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Checks runtime/manifest permission grants for the app's core capabilities:
 * microphone (speech input), fine location (GPS), and network access.
 */
class PermissionManager(private val context: Context) {

    fun hasRecordAudioPermission(): Boolean =
        hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasInternetPermission(): Boolean =
        hasPermission(Manifest.permission.INTERNET)

    fun hasAllRequiredPermissions(): Boolean =
        hasRecordAudioPermission() && hasLocationPermission() && hasInternetPermission()

    /**
     * Fires the system permission dialog for any not-yet-granted runtime permission
     * (RECORD_AUDIO, ACCESS_FINE_LOCATION) and returns whether they were ALREADY all
     * granted *before* this call.
     *
     * Permission requests are asynchronous on Android — the user's decision is
     * never available as a synchronous return value. It arrives later via
     * [Activity.onRequestPermissionsResult] (or an ActivityResultLauncher). Callers
     * that need the post-request outcome must observe that callback — or, in
     * Compose, drive the request with Accompanist's rememberMultiplePermissionsState
     * instead of calling this method directly.
     */
    fun requestPermissions(activity: Activity): Boolean {
        val alreadyGranted = hasAllRequiredPermissions()
        if (!alreadyGranted) {
            val missing = RUNTIME_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
        return alreadyGranted
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val PERMISSION_REQUEST_CODE = 4201

        val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
