package dev.pgm.roadmate.data.repository

import android.util.Log
import dev.pgm.roadmate.domain.model.SilenceAction
import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.ml.AudioLevelDetector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class SilenceDetectionRepositoryImpl @Inject constructor() : SilenceDetectionRepository {

    override fun observeSilence(durationMs: Long, thresholdDb: Double): Flow<SilenceEvent> =
        callbackFlow {
            val detector = AudioLevelDetector(
                silenceThresholdDb = thresholdDb,
                silenceDurationMs = durationMs,
                onSilenceDetected = { duration ->
                    trySend(SilenceEvent(System.currentTimeMillis(), duration, SilenceAction.ALERT))
                }
            )
            // The mic can be refused (permission revoked mid-session, another
            // owner holding it). Complete the flow rather than leaving the
            // collector subscribed to a monitor that will never emit.
            @Suppress("MissingPermission") // callers gate on RECORD_AUDIO; start() also fails soft
            val started = detector.start()
            if (!started) {
                Log.w(TAG, "could not open the microphone for silence detection")
                close()
                return@callbackFlow
            }
            awaitClose { detector.stop() }
        }

    private companion object {
        const val TAG = "SilenceDetection"
    }
}
