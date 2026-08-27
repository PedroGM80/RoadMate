package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import javax.inject.Inject

/**
 * Starts listening and returns the recognized text once the platform recognizer
 * detects the end of the user's utterance (its own end-pointing, not a separate
 * silence watchdog — see SpeechRecognitionRepository).
 */
class RecordAudioUseCase @Inject constructor(
    private val speechRecognitionRepository: SpeechRecognitionRepository
) {
    suspend operator fun invoke(): String = speechRecognitionRepository.recognizeSpeech()
}
