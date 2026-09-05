package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.audio.PcmAudioSource

/**
 * Offline speech-to-text over an audio stream the caller provides, for the
 * cases where the recognizer must not open the microphone itself — see
 * [dev.pgm.roadmate.domain.audio.PcmAudioSource] for why the car is one.
 *
 * [SpeechRecognitionRepository] stays the path for the phone, where the
 * recognizer owning its own mic session is the simpler arrangement.
 */
interface PcmTranscriber {

    /**
     * Records one utterance from [source] and returns what was said, or "" if
     * nothing was recognised, the stream could not be opened, or the model is
     * unavailable. Opens and closes [source] itself.
     */
    suspend fun transcribe(source: PcmAudioSource): String
}
