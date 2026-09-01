package dev.pgm.roadmate.presentation.viewmodel

import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.usecase.DetectSilenceUseCase
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeGeminiRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeGreetingRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeLocationRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeMapSearchRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeAssistantPreferencesRepository
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeMediaRepository
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
        locationFetchDelayMs: Long = 0L,
        greetingRepository: FakeGreetingRepository = FakeGreetingRepository(),
        greetingSpeechSynthesisRepository: FakeSpeechSynthesisRepository = FakeSpeechSynthesisRepository(),
        geminiRepository: FakeGeminiRepository = FakeGeminiRepository(geminiResponse),
        speechEvents: List<SpeechRecognitionEvent>? = null
    ): RoadMateViewModel {
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()
        val generateResponseUseCase = GenerateResponseUseCase(
            geminiRepository,
            speechSynthesisRepository,
            FakePhoneCallRepository(),
            FakeMapSearchRepository(),
            FakeMediaRepository(),
            FakeAssistantPreferencesRepository()
        )
        val speechRepo = if (speechEvents != null) {
            FakeSpeechRecognitionRepository(speechEvents)
        } else {
            FakeSpeechRecognitionRepository(recognizedSpeech)
        }
        val recordAudioUseCase = RecordAudioUseCase(speechRepo)
        val detectSilenceUseCase = DetectSilenceUseCase(FakeSilenceDetectionRepository(), generateResponseUseCase)
        val locationRepository = FakeLocationRepository(fetchDelayMs = locationFetchDelayMs, fetchResult = location)

        return RoadMateViewModel(
            recordAudioUseCase = recordAudioUseCase,
            generateResponseUseCase = generateResponseUseCase,
            detectSilenceUseCase = detectSilenceUseCase,
            locationRepository = locationRepository,
            weatherRepository = FakeWeatherRepository(),
            geminiRepository = geminiRepository,
            speechSynthesisRepository = greetingSpeechSynthesisRepository,
            greetingRepository = greetingRepository
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
    fun `partial speech results show up live in lastRecognizedInput`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = buildViewModel(
            geminiResponse = "vale",
            speechEvents = listOf(
                SpeechRecognitionEvent.Partial("cuánto"),
                SpeechRecognitionEvent.Partial("cuánto queda"),
                SpeechRecognitionEvent.Result("cuánto queda para llegar"),
            ),
        )

        viewModel.startListening()
        advanceUntilIdle()

        // The final Result wins, but partials drove the field while listening.
        assertEquals("cuánto queda para llegar", viewModel.uiState.value.lastRecognizedInput)
    }

    @Test
    fun `a recognition failure is surfaced and spoken instead of silently resetting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val spokenErrors = FakeSpeechSynthesisRepository()
            val viewModel = buildViewModel(
                greetingSpeechSynthesisRepository = spokenErrors,
                speechEvents = listOf(SpeechRecognitionEvent.Failed("No puedo acceder al micrófono.")),
            )

            viewModel.startListening()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(RoadMateStatus.IDLE, state.status)
            assertFalse(state.isListening)
            assertEquals("No puedo acceder al micrófono.", state.currentResponse)
            assertTrue(spokenErrors.spoken.contains("No puedo acceder al micrófono."))
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

    @Test
    fun `greetIfNeeded speaks and marks greeted when not greeted today`() = runTest(mainDispatcherRule.testDispatcher) {
        val greetingRepository = FakeGreetingRepository(shouldGreet = true)
        val greetingSpeech = FakeSpeechSynthesisRepository()
        val viewModel = buildViewModel(greetingRepository = greetingRepository, greetingSpeechSynthesisRepository = greetingSpeech)

        viewModel.greetIfNeeded()
        advanceUntilIdle()

        assertEquals(1, greetingRepository.markedGreetedCount)
        assertTrue(greetingSpeech.spoken.isNotEmpty())
    }

    @Test
    fun `greetIfNeeded stays silent when already greeted today`() = runTest(mainDispatcherRule.testDispatcher) {
        val greetingRepository = FakeGreetingRepository(shouldGreet = false)
        val greetingSpeech = FakeSpeechSynthesisRepository()
        val viewModel = buildViewModel(greetingRepository = greetingRepository, greetingSpeechSynthesisRepository = greetingSpeech)

        viewModel.greetIfNeeded()
        advanceUntilIdle()

        assertEquals(0, greetingRepository.markedGreetedCount)
        assertTrue(greetingSpeech.spoken.isEmpty())
    }

    @Test
    fun `local AI status is mirrored into uiState and a missing model auto-starts the download`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val geminiRepository = FakeGeminiRepository()
            geminiRepository.localAiStatusFlow.value = LocalAiStatus.ModelDownloadable
            val viewModel = buildViewModel(geminiRepository = geminiRepository)
            advanceUntilIdle()

            assertEquals(LocalAiStatus.ModelDownloadable, viewModel.uiState.value.localAiStatus)
            // No button tap: ModelDownloadable alone kicks the fetch.
            assertEquals(1, geminiRepository.downloadRequestedCount)

            geminiRepository.localAiStatusFlow.value = LocalAiStatus.Downloading(0.5f)
            advanceUntilIdle()

            assertEquals(LocalAiStatus.Downloading(0.5f), viewModel.uiState.value.localAiStatus)
        }

    @Test
    fun `a ready local backend never triggers a model download`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val geminiRepository = FakeGeminiRepository() // defaults to ReadyAicore
            val viewModel = buildViewModel(geminiRepository = geminiRepository)
            advanceUntilIdle()

            assertEquals(LocalAiStatus.ReadyAicore, viewModel.uiState.value.localAiStatus)
            assertEquals(0, geminiRepository.downloadRequestedCount)
        }

    @Test
    fun `downloadLocalAiModel asks the repository to fetch the model`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val geminiRepository = FakeGeminiRepository()
            val viewModel = buildViewModel(geminiRepository = geminiRepository)

            viewModel.downloadLocalAiModel()
            advanceUntilIdle()

            assertEquals(1, geminiRepository.downloadRequestedCount)
        }
}
