package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.data.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device wake-word ("RoadMate") detection via Picovoice Porcupine. Runs a
 * tiny always-on model on a background thread that Porcupine owns; when it
 * hears the keyword it fires [detections].
 *
 * Needs two things, both per-machine and gitignored:
 *  - `PICOVOICE_ACCESS_KEY` in `local.properties` (free from
 *    console.picovoice.ai) → [BuildConfig.PICOVOICE_ACCESS_KEY].
 *  - a trained keyword `wake/roadmate.ppn` and its matching language params
 *    `wake/porcupine_params.pv` in this module's assets.
 * Missing any of them, [isConfigured] is false and [detections] completes
 * immediately so the app falls back to the mic button.
 *
 * Porcupine's `PorcupineManager` holds its own `AudioRecord`; it must not run
 * at the same time as Vosk's `SpeechService` or the rest-reminder monitor, so
 * the collector is expected to stop this while any of those hold the mic.
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
    }

    /** Key present and both asset files bundled — worth trying to start. */
    fun isConfigured(): Boolean =
        BuildConfig.PICOVOICE_ACCESS_KEY.isNotBlank() &&
            assetExists(KEYWORD_ASSET) &&
            assetExists(PARAMS_ASSET)

    fun detections(): Flow<Unit> = callbackFlow {
        if (!isConfigured()) {
            DebugTrace.log("wake: not configured (key/asset missing) — mic button only")
            close()
            return@callbackFlow
        }

        val keywordPath = runCatching { unpackAsset(KEYWORD_ASSET) }.getOrNull()
        val paramsPath = runCatching { unpackAsset(PARAMS_ASSET) }.getOrNull()
        if (keywordPath == null || paramsPath == null) {
            DebugTrace.log("wake: could not unpack model assets — mic button only")
            close()
            return@callbackFlow
        }

        val manager = try {
            PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .setModelPath(paramsPath)
                .setKeywordPath(keywordPath)
                .setSensitivity(SENSITIVITY)
                .setErrorCallback { e -> DebugTrace.log("wake: engine error: ${e.message}") }
                .build(context) { keywordIndex ->
                    DebugTrace.log("wake: heard the wake word (#$keywordIndex)")
                    trySend(Unit)
                }
        } catch (e: PorcupineException) {
            Log.w(TAG, "Porcupine init failed", e)
            DebugTrace.log("wake: init failed: ${e.message} — mic button only")
            close()
            return@callbackFlow
        }

        try {
            manager.start()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Porcupine start failed", e)
            DebugTrace.log("wake: start failed: ${e.message} — mic button only")
            runCatching { manager.delete() }
            close()
            return@callbackFlow
        }
        DebugTrace.log("wake: listening for \"RoadMate\"")

        awaitClose {
            runCatching { manager.stop() }
            runCatching { manager.delete() }
            DebugTrace.log("wake: stopped")
        }
    }

    private fun assetExists(name: String): Boolean =
        runCatching { context.assets.open("$ASSET_DIR/$name").close() }.isSuccess

    /** Porcupine reads model files from disk, not from the asset manager. */
    private fun unpackAsset(name: String): String {
        val outDir = File(context.filesDir, ASSET_DIR).apply { mkdirs() }
        val outFile = File(outDir, name)
        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open("$ASSET_DIR/$name").use { input ->
                outFile.outputStream().use { input.copyTo(it) }
            }
        }
        return outFile.absolutePath
    }

    private companion object {
        const val TAG = "WakeWordDetector"
        const val ASSET_DIR = "wake"
        const val KEYWORD_ASSET = "roadmate.ppn"
        const val PARAMS_ASSET = "porcupine_params.pv"

        /** 0..1; higher = fewer misses, more false triggers. 0.5 is Picovoice's default. */
        const val SENSITIVITY = 0.6f
    }
}
