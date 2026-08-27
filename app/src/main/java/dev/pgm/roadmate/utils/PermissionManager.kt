package dev.pgm.roadmate.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
