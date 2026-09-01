package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for transcribing a single spoken utterance to text, offline.
 *
 * The end of the utterance is detected by the recognizer's own end-pointing
 * (it stops once the user pauses) — callers don't need a separate silence
 * watchdog. The stream emits [SpeechRecognitionEvent.Partial] updates while
 * the user speaks, then a single terminal [SpeechRecognitionEvent.Result] or
 * [SpeechRecognitionEvent.Failed].
 */
interface SpeechRecognitionRepository {

    fun recognize(): Flow<SpeechRecognitionEvent>
}
