package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Listens to one utterance and streams recognition events: live
 * [SpeechRecognitionEvent.Partial]s so the UI can show what's being heard,
 * then a terminal [SpeechRecognitionEvent.Result] or
 * [SpeechRecognitionEvent.Failed]. End-of-speech is the recognizer's own
 * call, not a separate silence watchdog.
 *
 * Before the mic opens it silences any answer/greeting still being spoken and
 * waits for the audio to drain — otherwise the recognizer hears RoadMate's
 * own TTS and transcribes it as the next question (a feedback loop, worst in
 * a car where mic and speaker sit together).
 */
class RecordAudioUseCase @Inject constructor(
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
) {
    operator fun invoke(): Flow<SpeechRecognitionEvent> = flow {
        hushBeforeListening()
        emitAll(speechRecognitionRepository.recognize())
    }

    /**
     * Convenience for callers that only need the final transcript (the
     * Android Auto screen): collects the stream and returns the last
     * [SpeechRecognitionEvent.Result] text, or "" on failure / nothing said.
     */
    suspend fun finalText(): String {
        hushBeforeListening()
        var text = ""
        speechRecognitionRepository.recognize().collect { event ->
            if (event is SpeechRecognitionEvent.Result) text = event.text
        }
        return text
    }

    private suspend fun hushBeforeListening() {
        speechSynthesisRepository.stop()
        speechSynthesisRepository.awaitDoneSpeaking()
        delay(TTS_SETTLE_MS.milliseconds)
    }

    private companion object {
        /** Let the audio output buffer drain after stop() before opening the mic. */
        const val TTS_SETTLE_MS = 150L
    }
}
