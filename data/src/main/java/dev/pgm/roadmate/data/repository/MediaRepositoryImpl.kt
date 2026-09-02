package dev.pgm.roadmate.data.repository

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Starts music playing in an app the driver already has. Nothing is queried
 * or sent anywhere beyond that app: RoadMate fires the system "play music on
 * X" intent, then — because some players just come to the foreground and
 * wait — follows it with a PLAY media-key so playback actually begins.
 */
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    override fun launchMediaApp(app: MediaApp): Boolean {
        // The intent Google Assistant uses for "play music on Spotify". An
        // empty query means "just play something" (resume / a recommended mix).
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(app.packageName)
            putExtra(SearchManager.QUERY, "")
            putExtra("android.intent.extra.START_PLAYBACK", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = play.resolveActivity(context.packageManager) != null &&
            runCatching { context.startActivity(play); true }
                .onFailure { Log.w(TAG, "play-from-search failed for ${app.packageName}", it) }
                .getOrDefault(false)

        // Whether or not the app understood the play intent, make sure it's
        // open, then nudge the session so a player that only foregrounded
        // actually starts.
        val opened = started || launchPlain(app)
        if (opened) nudgePlay()
        return opened
    }

    private fun launchPlain(app: MediaApp): Boolean {
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

    /**
     * Emits a PLAY media-key once the app has had a beat to register its
     * media session. The system routes it to the active session, so this also
     * covers a player that ignored the play intent and merely opened.
     */
    private fun nudgePlay() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }.onFailure { Log.w(TAG, "media-key nudge failed", it) }
        }, PLAY_NUDGE_DELAY_MS)
    }

    override fun launchAnyMusicApp(): MediaApp? =
        MediaApp.entries.firstOrNull { isInstalled(it) && launchMediaApp(it) }

    private fun isInstalled(app: MediaApp): Boolean =
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null

    private companion object {
        const val TAG = "MediaRepository"
        const val PLAY_NUDGE_DELAY_MS = 1_600L
    }
}
