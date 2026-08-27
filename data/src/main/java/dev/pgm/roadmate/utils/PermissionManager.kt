package dev.pgm.roadmate.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Checks runtime/manifest permission grants for the app's core capabilities:
 * microphone (speech input), fine location (GPS), and network access.
 *
 * Only exposes checks, not a request method: the actual system permission
 * dialog is driven from Compose via Accompanist's
 * rememberMultiplePermissionsState (see HomeScreen) — that's the only place
 * in the app that needs to trigger a request, and Accompanist already models
 * the asynchronous result as observable state, which a request method on
 * this class couldn't do without duplicating that machinery.
 */
class PermissionManager @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasRecordAudioPermission(): Boolean =
        hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasInternetPermission(): Boolean =
        hasPermission(Manifest.permission.INTERNET)

    fun hasAllRequiredPermissions(): Boolean =
        hasRecordAudioPermission() && hasLocationPermission() && hasInternetPermission()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
