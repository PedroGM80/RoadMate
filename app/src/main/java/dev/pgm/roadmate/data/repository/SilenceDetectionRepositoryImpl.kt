package dev.pgm.roadmate.data.repository

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
            detector.start()
            awaitClose { detector.stop() }
        }
}
