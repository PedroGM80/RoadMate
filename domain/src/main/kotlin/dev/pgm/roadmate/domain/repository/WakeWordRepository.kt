package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for hands-free activation: an always-on, low-power listener that
 * emits once every time the driver says the wake word ("RoadMate"), so the
 * caller can open the mic for the real question with no button press.
 *
 * Fully on-device — no network, no account beyond a build-time key. The
 * stream simply completes (emitting nothing) when wake-word support is
 * unavailable: no Picovoice AccessKey configured, no trained keyword bundled,
 * or the engine failed to start. Callers treat that as "mic button only".
 *
 * The listener and the rest-reminder silence monitor both want the
 * microphone continuously, so callers run only one at a time — see
 * [isAvailable].
 */
interface WakeWordRepository {

    /**
     * True when a key and a keyword file are both present, i.e. starting the
     * engine is worth attempting. Cheap — checks configuration, not the
     * native engine. A false here means callers should keep the silence
     * monitor running instead.
     */
    fun isAvailable(): Boolean

    /** Emits [Unit] on every wake-word detection until the collector stops. */
    fun detections(): Flow<Unit>
}
