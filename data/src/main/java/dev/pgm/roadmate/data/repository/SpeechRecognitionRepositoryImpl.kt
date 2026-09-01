package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.ml.VoskSpeechRecognizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Offline speech-to-text, delegated to [VoskSpeechRecognizer] (Vosk/Kaldi) —
 * no Google speech pack, no network, no account.
 */
class SpeechRecognitionRepositoryImpl @Inject constructor(
    private val voskSpeechRecognizer: VoskSpeechRecognizer
) : SpeechRecognitionRepository {

    override fun recognize(): Flow<SpeechRecognitionEvent> = voskSpeechRecognizer.recognize()
}
