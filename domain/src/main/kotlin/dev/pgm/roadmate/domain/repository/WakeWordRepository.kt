package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for hands-free activation: an always-on listener that emits once
 * every time the driver says the wake phrase ("oye copiloto"), so the caller
 * can open the mic for the real question with no button press.
 *
 * Fully on-device — no network, no account, no extra dependency (it reuses
 * the bundled Vosk model). The stream simply completes (emitting nothing)
 * when the model can't be loaded; callers treat that as "mic button only".
 *
 * The listener and the rest-reminder silence monitor both want the
 * microphone continuously, so callers run only one at a time — see
 * [isAvailable].
 */
interface WakeWordRepository {

    /**
     * True when hands-free listening can run. Currently always true (the
     * model is bundled); a user-facing off switch would gate this. A false
     * means callers should keep the silence monitor running instead.
     */
    fun isAvailable(): Boolean

    /** Emits [Unit] on every wake-word detection until the collector stops. */
    fun detections(): Flow<Unit>
}
