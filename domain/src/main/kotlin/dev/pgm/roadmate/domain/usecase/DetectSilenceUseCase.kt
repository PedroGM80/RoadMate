package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Watches the cabin's ambient audio and emits a [SilenceEvent] whenever the
 * driver has gone quiet for [Constants.REST_REMINDER_SILENCE_MS]. Callers pair
 * each event with [GenerateResponseUseCase] using one of the predefined
 * rest-reminder prompts to produce and speak an actual response.
 */
class DetectSilenceUseCase @Inject constructor(
    private val silenceDetectionRepository: SilenceDetectionRepository,
    private val generateResponseUseCase: GenerateResponseUseCase
) {
    fun observeSilence(): Flow<SilenceEvent> =
        silenceDetectionRepository.observeSilence(
            durationMs = Constants.REST_REMINDER_SILENCE_MS,
            thresholdDb = Constants.SILENCE_THRESHOLD_DB
        )

    fun triggerRestPrompt(context: TravelContext): Flow<String> {
        val prompt = Constants.REST_REMINDER_PROMPTS.random()
        return generateResponseUseCase(context, prompt)
    }
}
