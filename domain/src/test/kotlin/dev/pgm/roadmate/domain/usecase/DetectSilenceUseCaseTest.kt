package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeAssistantPreferencesRepository
import dev.pgm.roadmate.domain.fake.FakeGeminiRepository
import dev.pgm.roadmate.domain.fake.FakeMapSearchCoordinator
import dev.pgm.roadmate.domain.fake.FakeMediaRepository
import dev.pgm.roadmate.domain.fake.FakeMemoryRepository
import dev.pgm.roadmate.domain.fake.FakePhoneCallRepository
import dev.pgm.roadmate.domain.fake.FakeSilenceDetectionRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.fake.FakeWeatherRepository
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class DetectSilenceUseCaseTest {

    @Test
    fun `observeSilence uses the rest-reminder threshold and duration`() {
        val silenceDetectionRepository = FakeSilenceDetectionRepository()
        val generateResponseUseCase = GenerateResponseUseCase(
            FakeGeminiRepository(),
            FakeSpeechSynthesisRepository(),
            FakePhoneCallRepository(),
            FakeMapSearchCoordinator(),
            FakeMediaRepository(),
            FakeAssistantPreferencesRepository(),
            FakeMemoryRepository(),
            FakeWeatherRepository(),
            dev.pgm.roadmate.domain.fake.FakeMessagingRepository(),
            dev.pgm.roadmate.domain.fake.FakeReminderRepository(),
        dev.pgm.roadmate.domain.fake.FakeCalendarRepository()
        )
        val useCase = DetectSilenceUseCase(silenceDetectionRepository, generateResponseUseCase)

        useCase.observeSilence()

        assertEquals(Constants.REST_REMINDER_SILENCE_MS, silenceDetectionRepository.lastDurationMs)
        assertEquals(Constants.SILENCE_THRESHOLD_DB, silenceDetectionRepository.lastThresholdDb)
    }

    @Test
    fun `triggerRestPrompt picks one of the predefined prompts and generates a response`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "haz una pausa")
        val generateResponseUseCase = GenerateResponseUseCase(
            geminiRepository,
            FakeSpeechSynthesisRepository(),
            FakePhoneCallRepository(),
            FakeMapSearchCoordinator(),
            FakeMediaRepository(),
            FakeAssistantPreferencesRepository(),
            FakeMemoryRepository(),
            FakeWeatherRepository(),
            dev.pgm.roadmate.domain.fake.FakeMessagingRepository(),
            dev.pgm.roadmate.domain.fake.FakeReminderRepository(),
        dev.pgm.roadmate.domain.fake.FakeCalendarRepository()
        )
        val useCase = DetectSilenceUseCase(FakeSilenceDetectionRepository(), generateResponseUseCase)

        val context = TravelContext(null, null, 0, Date(), userInput = "")
        val emitted = useCase.triggerRestPrompt(context).toList()

        assertEquals(listOf("haz una pausa"), emitted)
        assertTrue(Constants.REST_REMINDER_PROMPTS.any { geminiRepository.lastPrompt!!.contains(it) })
    }
}
