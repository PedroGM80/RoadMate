package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Listens to one utterance and streams recognition events: live
 * [SpeechRecognitionEvent.Partial]s so the UI can show what's being heard,
 * then a terminal [SpeechRecognitionEvent.Result] or
 * [SpeechRecognitionEvent.Failed]. End-of-speech is the recognizer's own
 * call, not a separate silence watchdog.
 */
class RecordAudioUseCase @Inject constructor(
    private val speechRecognitionRepository: SpeechRecognitionRepository
) {
    operator fun invoke(): Flow<SpeechRecognitionEvent> = speechRecognitionRepository.recognize()

    /**
     * Convenience for callers that only need the final transcript (the
     * Android Auto screen): collects the stream and returns the last
     * [SpeechRecognitionEvent.Result] text, or "" on failure / nothing said.
     */
    suspend fun finalText(): String {
        var text = ""
        speechRecognitionRepository.recognize().collect { event ->
            if (event is SpeechRecognitionEvent.Result) text = event.text
        }
        return text
    }
}
