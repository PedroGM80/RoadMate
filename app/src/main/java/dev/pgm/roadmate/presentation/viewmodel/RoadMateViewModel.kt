package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.GreetingRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.DetectSilenceUseCase
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject

enum class RoadMateStatus { IDLE, LISTENING, PROCESSING, SPEAKING }

data class RoadMateUiState(
    val status: RoadMateStatus = RoadMateStatus.IDLE,
    val lastRecognizedInput: String = "",
    val currentResponse: String = "",
    val location: Pair<Double, Double>? = null,
    val locationUnavailable: Boolean = false,
    val isListening: Boolean = false,
    /** null while checking, then whether on-device Gemini Nano actually works on this hardware. */
    val isLocalAiAvailable: Boolean? = null
)

@HiltViewModel
class RoadMateViewModel @Inject constructor(
    private val recordAudioUseCase: RecordAudioUseCase,
    private val generateResponseUseCase: GenerateResponseUseCase,
    private val detectSilenceUseCase: DetectSilenceUseCase,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val greetingRepository: GreetingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoadMateUiState())
    val uiState: StateFlow<RoadMateUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null
    private var silenceMonitoringJob: Job? = null

    init {
        viewModelScope.launch {
            locationRepository.location.collect { location ->
                _uiState.value = _uiState.value.copy(
                    location = location,
                    locationUnavailable = if (location != null) false else _uiState.value.locationUnavailable
                )
            }
        }

        // Checked once at startup rather than discovered per-answer, so the UI
        // can be upfront about "modo básico" instead of the user only finding
        // out by getting generic fallback text on their first real question.
        viewModelScope.launch {
            val available = geminiRepository.isLocalAiAvailable()
            _uiState.value = _uiState.value.copy(isLocalAiAvailable = available)
        }
    }

    /**
     * Proactively fetches a GPS fix instead of waiting for the first voice
     * question to trigger one (buildTravelContext() would otherwise be the
     * only thing that ever called this, leaving the location chip stuck on
     * "buscando ubicación..." indefinitely until the user asked something).
     * Gives up after LOCATION_TIMEOUT_MS and surfaces that as
     * locationUnavailable rather than spinning forever.
     */
    fun refreshLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(locationUnavailable = false)
            val location = withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS) {
                locationRepository.getCurrentCoordinates()
            }
            if (location == null) {
                _uiState.value = _uiState.value.copy(locationUnavailable = true)
            }
        }
    }

    /**
     * Speaks a time-of-day greeting the first time the app is opened with
     * core permissions granted on a given calendar day — called from the
     * same permission-gated place as [refreshLocation], not from init{},
     * so it never speaks before the user can actually hear a purposeful
     * answer (i.e. before mic/location permissions exist).
     */
    fun greetIfNeeded() {
        viewModelScope.launch {
            if (greetingRepository.shouldGreetToday()) {
                greetingRepository.markGreetedToday()
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                speechSynthesisRepository.speak(Constants.greetingForHour(hour))
            }
        }
    }

    /**
     * Starts the rest-reminder silence monitor. Must be called only after RECORD_AUDIO
     * has actually been granted — AudioRecord initialization fails silently otherwise,
     * and unlike startListening() there's no user action to retry it from, so calling
     * this too early (e.g. unconditionally from init{}) would leave the monitor dead
     * for the rest of the ViewModel's lifetime. HomeScreen calls this once its
     * permission state flips to granted.
     *
     * Only one thing should hold the mic for silence detection at a time: MainActivity
     * stops this in onPause() (handing off to SilenceDetectionForegroundService while
     * backgrounded) and restarts it in onResume().
     */
    fun startSilenceMonitoring() {
        if (silenceMonitoringJob?.isActive == true) return
        silenceMonitoringJob = detectSilenceUseCase.observeSilence()
            .onEach { handleRestReminder() }
            .catch { /* transient audio read errors shouldn't kill the monitor */ }
            .launchIn(viewModelScope)
    }

    fun stopSilenceMonitoring() {
        silenceMonitoringJob?.cancel()
        silenceMonitoringJob = null
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return
        listeningJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RoadMateStatus.LISTENING, isListening = true)

            val userInput = recordAudioUseCase()
            if (userInput.isBlank()) {
                _uiState.value = _uiState.value.copy(status = RoadMateStatus.IDLE, isListening = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                status = RoadMateStatus.PROCESSING,
                lastRecognizedInput = userInput
            )
            respondTo(userInput)
        }
    }

    fun cancelListening() {
        listeningJob?.cancel()
        listeningJob = null
        _uiState.value = _uiState.value.copy(status = RoadMateStatus.IDLE, isListening = false)
    }

    // These previously used .onEach{}.launchIn(viewModelScope) — a detached,
    // unawaited child coroutine. Since both are already suspend functions
    // called from inside a viewModelScope.launch{} started by the caller,
    // there's no reason not to just collect directly: same production
    // behavior, but now genuinely finishes before the calling coroutine does
    // instead of racing it (caught by a ViewModel unit test asserting state
    // right after startListening() returned).
    private suspend fun respondTo(userInput: String) {
        val travelContext = buildTravelContext(userInput)
        generateResponseUseCase(travelContext, userInput)
            .catch {
                _uiState.value = _uiState.value.copy(status = RoadMateStatus.IDLE, isListening = false)
            }
            .collect { response ->
                _uiState.value = _uiState.value.copy(
                    status = RoadMateStatus.SPEAKING,
                    currentResponse = response,
                    isListening = false
                )
            }
    }

    private suspend fun handleRestReminder() {
        val travelContext = buildTravelContext(userInput = "")
        detectSilenceUseCase.triggerRestPrompt(travelContext)
            .collect { response ->
                _uiState.value = _uiState.value.copy(
                    status = RoadMateStatus.SPEAKING,
                    currentResponse = response
                )
            }
    }

    private suspend fun buildTravelContext(userInput: String): TravelContext {
        val location = locationRepository.getCurrentCoordinates()
        val weatherDescription = location?.let { (lat, lon) ->
            weatherRepository.getCurrentWeatherDescription(lat, lon)
        }
        val calendar = Calendar.getInstance()
        return TravelContext(
            currentLocation = location,
            destination = null,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            date = calendar.time,
            userInput = userInput,
            lastResponses = listOfNotNull(_uiState.value.currentResponse.takeIf { it.isNotBlank() }),
            weatherDescription = weatherDescription
        )
    }

    override fun onCleared() {
        super.onCleared()
        cancelListening()
    }
}
