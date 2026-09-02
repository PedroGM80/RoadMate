package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.GreetingRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.DetectSilenceUseCase
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.utils.QuestionPunctuation
import dev.pgm.roadmate.utils.SpokenText
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
    /** True when [currentResponse] is an error/nudge, not a real answer. */
    val isError: Boolean = false,
    val location: Pair<Double, Double>? = null,
    val locationUnavailable: Boolean = false,
    val isListening: Boolean = false,
    /** State of on-device AI: ready (AICore or downloaded model), available
     *  to download, downloading, or stuck in "modo básico". */
    val localAiStatus: LocalAiStatus = LocalAiStatus.Checking
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
    private val greetingRepository: GreetingRepository,
    private val wakeWordRepository: WakeWordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoadMateUiState())
    val uiState: StateFlow<RoadMateUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null
    private var silenceMonitoringJob: Job? = null
    private var wakeWordJob: Job? = null

    /**
     * Whether hands-free listening *should* be running — the caller's intent,
     * kept separately from [wakeWordJob] so the internal pause/resume around a
     * Vosk capture doesn't get confused with a real stop from onPause().
     */
    private var wakeWordDesired = false

    init {
        viewModelScope.launch {
            locationRepository.location.collect { location ->
                _uiState.value = _uiState.value.copy(
                    location = location,
                    locationUnavailable = if (location != null) false else _uiState.value.locationUnavailable
                )
            }
        }

        // Surfaced from startup rather than discovered per-answer, so the UI
        // can show progress instead of the user only finding out via generic
        // fallback text on their first real question. Keeps flowing so
        // download progress lands in the UI live. When there's no AICore and
        // the model isn't here yet, the download starts on its own — no tap
        // needed — and LocalAiModelManager still holds it to Wi-Fi.
        geminiRepository.localAiStatus()
            .onEach { status ->
                _uiState.value = _uiState.value.copy(localAiStatus = status)
                if (status == LocalAiStatus.ModelDownloadable) downloadLocalAiModel()
                // Model on disk but no AICore → pre-load it now so the first
                // question isn't a ~10 s cold start.
                if (status == LocalAiStatus.ReadyLocalModel) {
                    viewModelScope.launch { geminiRepository.warmUp() }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Triggers the one-time (~550 MB, Wi-Fi-only) download of the local model
     * that powers on-device answers where AICore is absent. Called
     * automatically when the model is missing, and also wired to a manual
     * "retry" button. Progress and completion arrive through [uiState]'s
     * `localAiStatus`.
     */
    fun downloadLocalAiModel() {
        viewModelScope.launch { geminiRepository.requestLocalAiModelDownload() }
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
            // Ignore a "silence over" event that fires while RoadMate itself is
            // talking — the mic is only hearing our own TTS, not the driver.
            .onEach { if (!speechSynthesisRepository.isSpeaking.value) handleRestReminder() }
            .catch { /* transient audio read errors shouldn't kill the monitor */ }
            .launchIn(viewModelScope)
    }

    fun stopSilenceMonitoring() {
        silenceMonitoringJob?.cancel()
        silenceMonitoringJob = null
    }

    /** True when hands-free listening can run — used to decide whether it
     *  replaces the mic-button-plus-silence-monitor path. */
    fun isWakeWordAvailable(): Boolean = wakeWordRepository.isAvailable()

    /**
     * Starts whichever always-on mic consumer applies: the "oye copiloto"
     * wake-phrase listener when it's available, otherwise the rest-reminder
     * silence monitor. They can't both hold the mic, so this deliberately
     * runs only one. Call once RECORD_AUDIO is granted (HomeScreen) and on
     * every onResume() (MainActivity).
     */
    fun startAmbientListening() {
        if (wakeWordRepository.isAvailable()) {
            stopSilenceMonitoring()
            startWakeWordListening()
        } else {
            startSilenceMonitoring()
        }
    }

    /** Stops both, whichever was running. */
    fun stopAmbientListening() {
        stopWakeWordListening()
        stopSilenceMonitoring()
    }

    /**
     * Starts the hands-free wake-phrase listener ("oye copiloto"). No-op when
     * it isn't available (the app then relies on the mic button). Holds the
     * mic continuously, so it is mutually exclusive with the rest-reminder
     * silence monitor — callers start one or the other, not both.
     */
    fun startWakeWordListening() {
        wakeWordDesired = true
        if (wakeWordJob?.isActive == true || !wakeWordRepository.isAvailable()) return
        wakeWordJob = wakeWordRepository.detections()
            .onEach {
                // Ignore detections while we're already capturing a question or
                // still speaking — the recognizer would otherwise trip on the
                // assistant's own speech.
                if (listeningJob?.isActive != true && !speechSynthesisRepository.isSpeaking.value) {
                    startListening()
                }
            }
            .catch { /* an engine hiccup shouldn't take the app down */ }
            .launchIn(viewModelScope)
    }

    fun stopWakeWordListening() {
        wakeWordDesired = false
        wakeWordJob?.cancel()
        wakeWordJob = null
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return
        // Free the mic for Vosk: pause wake detection without clearing the
        // caller's intent, so the `finally` below can bring it back.
        val resumeWakeWord = wakeWordJob?.isActive == true
        wakeWordJob?.cancel()
        wakeWordJob = null
        listeningJob = viewModelScope.launch {
          try {
            _uiState.value = _uiState.value.copy(
                status = RoadMateStatus.LISTENING,
                isListening = true,
                lastRecognizedInput = "",
                currentResponse = "",
                isError = false
            )

            var finalText = ""
            var failure: String? = null
            recordAudioUseCase()
                .catch { failure = SpokenText.SPEECH_FLOW_ERROR }
                .collect { event ->
                    when (event) {
                        // Live transcription so the user sees what's being heard.
                        is SpeechRecognitionEvent.Partial ->
                            _uiState.value = _uiState.value.copy(lastRecognizedInput = event.text)

                        is SpeechRecognitionEvent.Result -> finalText = event.text

                        is SpeechRecognitionEvent.Failed -> failure = event.message
                    }
                }

            failure?.let { message ->
                _uiState.value = _uiState.value.copy(
                    status = RoadMateStatus.IDLE,
                    isListening = false,
                    currentResponse = message,
                    isError = true
                )
                speechSynthesisRepository.speak(message)
                return@launch
            }

            if (finalText.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    status = RoadMateStatus.IDLE,
                    isListening = false,
                    lastRecognizedInput = ""
                )
                return@launch
            }

            // Vosk returns bare lowercase text; punctuate an obvious question
            // so it reads right and the model gets a clearer signal.
            val recognized = QuestionPunctuation.normalize(finalText)
            _uiState.value = _uiState.value.copy(
                status = RoadMateStatus.PROCESSING,
                lastRecognizedInput = recognized
            )
            respondTo(recognized)
          } finally {
              // Mic is free again — bring hands-free listening back, unless
              // something (onPause) has since cleared the intent.
              if (resumeWakeWord && wakeWordDesired) startWakeWordListening()
          }
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
                    isListening = false,
                    isError = false
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
            minute = calendar.get(Calendar.MINUTE),
            date = calendar.time,
            userInput = userInput,
            weatherDescription = weatherDescription
        )
    }

    override fun onCleared() {
        super.onCleared()
        cancelListening()
        stopWakeWordListening()
    }
}
