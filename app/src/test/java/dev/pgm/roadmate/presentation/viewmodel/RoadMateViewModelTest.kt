package dev.pgm.roadmate.presentation.viewmodel

import dev.pgm.roadmate.domain.usecase.DetectSilenceUseCase
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeGeminiRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeLocationRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakePhoneCallRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeSilenceDetectionRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeSpeechRecognitionRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeWeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoadMateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        recognizedSpeech: String = "hola roadmate",
        geminiResponse: String = "respuesta de prueba",
        location: Pair<Double, Double>? = 36.46 to -6.19,
        locationFetchDelayMs: Long = 0L
    ): RoadMateViewModel {
        val geminiRepository = FakeGeminiRepository(geminiResponse)
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()
        val generateResponseUseCase =
            GenerateResponseUseCase(geminiRepository, speechSynthesisRepository, FakePhoneCallRepository())
        val recordAudioUseCase = RecordAudioUseCase(FakeSpeechRecognitionRepository(recognizedSpeech))
        val detectSilenceUseCase = DetectSilenceUseCase(FakeSilenceDetectionRepository(), generateResponseUseCase)
        val locationRepository = FakeLocationRepository(fetchDelayMs = locationFetchDelayMs, fetchResult = location)

        return RoadMateViewModel(
            recordAudioUseCase = recordAudioUseCase,
            generateResponseUseCase = generateResponseUseCase,
            detectSilenceUseCase = detectSilenceUseCase,
            locationRepository = locationRepository,
            weatherRepository = FakeWeatherRepository(),
            geminiRepository = geminiRepository
        )
    }

    @Test
    fun `startListening with recognized speech ends up SPEAKING with the response`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = buildViewModel(recognizedSpeech = "¿cuánto queda?", geminiResponse = "quedan 10 km")

        viewModel.startListening()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RoadMateStatus.SPEAKING, state.status)
        assertEquals("¿cuánto queda?", state.lastRecognizedInput)
        assertEquals("quedan 10 km", state.currentResponse)
        assertFalse(state.isListening)
    }

    @Test
    fun `startListening with no recognized speech resets to IDLE`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = buildViewModel(recognizedSpeech = "")

        viewModel.startListening()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RoadMateStatus.IDLE, state.status)
        assertFalse(state.isListening)
        assertTrue(state.currentResponse.isBlank())
    }

    @Test
    fun `cancelListening resets to IDLE`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = buildViewModel()

        viewModel.startListening()
        viewModel.cancelListening()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RoadMateStatus.IDLE, state.status)
        assertFalse(state.isListening)
    }

    @Test
    fun `refreshLocation surfaces a resolved fix`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = buildViewModel(location = 40.4168 to -3.7038)

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(40.4168 to -3.7038, state.location)
        assertFalse(state.locationUnavailable)
    }

    @Test
    fun `refreshLocation times out and flags locationUnavailable instead of hanging forever`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Longer than Constants.LOCATION_TIMEOUT_MS (10s) — advanced via virtual
            // time by runTest, not a real 15-second wait.
            val viewModel = buildViewModel(location = 40.4168 to -3.7038, locationFetchDelayMs = 15_000L)

            viewModel.refreshLocation()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.locationUnavailable)
            assertEquals(null, state.location)
        }
}
