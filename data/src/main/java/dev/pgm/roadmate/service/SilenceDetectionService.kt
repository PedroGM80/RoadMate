package dev.pgm.roadmate.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pgm.roadmate.ml.AudioLevelDetector
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Periodic WorkManager job (not a foreground Service — this follows the
 * spec's own guidance) that samples ambient audio for a short window and
 * reports whether the cabin has been silent, even while the app is
 * backgrounded.
 *
 * IMPORTANT CAVEAT: since Android 9 (API 28), apps in the background are
 * generally blocked from the microphone entirely unless they're a foreground
 * service, hold an active voice-interaction role, etc. A CoroutineWorker has
 * no such exemption, so mic access here may silently fail or throw on real
 * devices depending on OEM/Android version. If continuous background
 * detection genuinely needs to work while the app isn't in the foreground,
 * the correct tool is a foreground service declared with
 * android:foregroundServiceType="microphone" (foreground services are NOT
 * deprecated on Android 12+ — only implicit background starts are
 * restricted) — contrary to the spec's assumption. This worker still gives
 * best-effort periodic sampling and is wired up as requested, but that
 * caveat should be resolved before relying on it in production.
 *
 * WorkManager's minimum periodic interval is 15 minutes; the 30-minute rest
 * cadence used here is safely above that floor.
 */
class SilenceDetectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val wasSilent = sampleForSilence()
        if (wasSilent) {
            // TODO: surface a notification prompting the driver to rest.
        }
        return Result.success()
    }

    private suspend fun sampleForSilence(): Boolean = suspendCancellableCoroutine { continuation ->
        val detector = AudioLevelDetector(
            silenceThresholdDb = Constants.SILENCE_THRESHOLD_DB,
            silenceDurationMs = SAMPLE_WINDOW_MS,
            onSilenceDetected = {
                if (continuation.isActive) continuation.resume(true)
            }
        )
        continuation.invokeOnCancellation { detector.stop() }
        detector.start()
    }

    companion object {
        private const val SAMPLE_WINDOW_MS = 10_000L
        private const val WORK_NAME = "silence_detection_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SilenceDetectionWorker>(
                Constants.REST_REMINDER_SILENCE_MS, TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
