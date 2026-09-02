package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.ml.WakeWordDetector
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Hands-free activation, delegated to [WakeWordDetector] (Picovoice
 * Porcupine) — on-device, no network. No-ops when the engine isn't
 * configured (see [WakeWordDetector.isConfigured]).
 */
class WakeWordRepositoryImpl @Inject constructor(
    private val wakeWordDetector: WakeWordDetector,
) : WakeWordRepository {

    override fun isAvailable(): Boolean = wakeWordDetector.isConfigured()

    override fun detections(): Flow<Unit> = wakeWordDetector.detections()
}
