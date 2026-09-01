package dev.pgm.roadmate.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Launches a music app by its package's own launcher intent. Nothing is
 * queried or sent anywhere — RoadMate just brings the app the driver already
 * has to the foreground.
 */
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    override fun launchMediaApp(app: MediaApp): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not launch ${app.packageName}", e)
            false
        }
    }

    private companion object {
        const val TAG = "MediaRepository"
    }
}
