package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.SilenceEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for watching ambient audio and reporting sustained silence.
 *
 * Each call starts its own independent listening session (stopped when the
 * returned Flow's collector cancels), so the same implementation can serve
 * both a short "user stopped talking" watchdog and a long "suggest a rest
 * break" monitor with different parameters.
 */
interface SilenceDetectionRepository {

    fun observeSilence(durationMs: Long, thresholdDb: Double): Flow<SilenceEvent>
}
