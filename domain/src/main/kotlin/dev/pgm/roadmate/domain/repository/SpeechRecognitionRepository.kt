package dev.pgm.roadmate.domain.repository

/**
 * Contract for transcribing a single spoken utterance to text.
 *
 * The end of the utterance is detected by the platform recognizer's own
 * end-pointing (it stops listening once the user pauses) — callers don't
 * need to run a separate silence watchdog to know when speech ended.
 */
interface SpeechRecognitionRepository {

    /** Suspends until a result (or empty string on error/no match) is available. */
    suspend fun recognizeSpeech(): String
}
