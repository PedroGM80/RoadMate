package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.ml.WakeWordDetector
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Hands-free activation, delegated to [WakeWordDetector] (a grammar-limited
 * Vosk recognizer) — on-device, no network, no extra dependency.
 */
class WakeWordRepositoryImpl @Inject constructor(
    private val wakeWordDetector: WakeWordDetector,
) : WakeWordRepository {

    override fun isAvailable(): Boolean = wakeWordDetector.isConfigured()

    override fun detections(): Flow<Unit> = wakeWordDetector.detections()
}
