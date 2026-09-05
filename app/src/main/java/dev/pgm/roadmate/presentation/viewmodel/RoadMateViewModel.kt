package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.audio.Earcon
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.UserLocation
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.GreetingRepository
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The voice loop's state.
 *
 * IDLE — nothing (the "oye copiloto" listener runs when hands-free is on).
 * LISTENING — mic open, capturing a question.
 * PROCESSING — generating the answer.
 * SPEAKING — reading the answer aloud.
 * FOLLOW_UP — answer done, mic briefly open again for a follow-up without
 *   needing the wake phrase; a few seconds of silence drops back to IDLE.
 */
enum class RoadMateStatus { IDLE, LISTENING, PROCESSING, SPEAKING, FOLLOW_UP }

/** Which foreground service, if any, should take the mic when the app leaves the screen. */
enum class BackgroundListening { NONE, WAKE_WORD, REST_MONITOR }

data class RoadMateUiState(
    val status: RoadMateStatus = RoadMateStatus.IDLE,
    val lastRecognizedInput: String = "",
    val currentResponse: String = "",
    /** True when [currentResponse] is an error/nudge, not a real answer. */
    val isError: Boolean = false,
    val location: UserLocation? = null,
    /** "Calle X, Localidad · Provincia" when reverse geocoding resolved it. */
    val locationLabel: String? = null,
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
    private val wakeWordRepository: WakeWordRepository,
    assistantPreferencesRepository: AssistantPreferencesRepository,
    private val currentPlaceRepository: CurrentPlaceRepository
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

    /** Rising blip so a hands-free "oye copiloto" sounds like a mic-button tap. */
    private val wakeEarcon = Earcon()

    /**
     * The driver's "manos libres" setting; the wake phrase only runs when on.
     *
     * Null until DataStore has actually answered. The old `Eagerly, true`
     * seed meant a driver who had switched hands-free *off* still had the wake
     * recognizer — and the microphone — opened for the moment between launch
     * and the first read, then torn down again. Ambient listening now simply
     * waits for a real value: it is a few milliseconds, and nothing should
     * touch the mic on a guess about a privacy setting.
     */
    val handsFreeEnabled: StateFlow<Boolean?> = assistantPreferencesRepository.handsFreeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            locationRepository.location.collect { location ->
                _uiState.value = _uiState.value.copy(
                    location = location,
                    locationUnavailable = location == null && _uiState.value.locationUnavailable,
                )
            }
        }

        // Street/locality label — resolved from the offline map tiles by the
        // map layer, never a network geocoder.
        viewModelScope.launch {
            currentPlaceRepository.label.collect { label ->
                _uiState.value = _uiState.value.copy(locationLabel = label)
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
            val location = withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS.milliseconds) {
                locationRepository.currentLocation()
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

    /** True when the wake phrase should be the active mic consumer: the
     *  engine is available *and* the driver hasn't switched "manos libres"
     *  off. Used to pick the foreground and background paths. */
    fun handsFreeActive(): Boolean =
        handsFreeEnabled.value == true && wakeWordRepository.isAvailable()

    /**
     * Which mic consumer should hold the microphone while the app is
     * backgrounded. The policy lives here rather than in MainActivity so the
     * "setting not read yet → claim nothing" rule is stated once and can be
     * tested.
     */
    fun backgroundListening(): BackgroundListening = when {
        handsFreeEnabled.value == null -> BackgroundListening.NONE
        handsFreeActive() -> BackgroundListening.WAKE_WORD
        else -> BackgroundListening.REST_MONITOR
    }

    /**
     * Starts whichever always-on mic consumer applies: the "oye copiloto"
     * wake-phrase listener when [handsFreeActive], otherwise the rest-reminder
     * silence monitor. They can't both hold the mic, so this deliberately
     * runs only one. Call once RECORD_AUDIO is granted (HomeScreen), on every
     * onResume() (MainActivity), and whenever the setting flips.
     */
    fun startAmbientListening() {
        // Setting not read yet — claim no mic at all. HomeScreen re-runs this
        // as soon as the value lands.
        if (handsFreeEnabled.value == null) return
        if (handsFreeActive()) {
            stopSilenceMonitoring()
            startWakeWordListening()
        } else {
            stopWakeWordListening()
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
                    runCatching { wakeEarcon.start() }
                    // Acknowledge out loud, then open the mic — wait for the
                    // "Sí, dime." to finish so STT doesn't hear it.
                    speechSynthesisRepository.speak(SpokenText.WAKE_ACK)
                    speechSynthesisRepository.awaitDoneSpeaking()
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
        val wakeJob = wakeWordJob
        val resumeWakeWord = wakeJob?.isActive == true
        wakeWordJob = null
        listeningJob = viewModelScope.launch {
            // cancelAndJoin, not cancel: the wake recognizer's AudioRecord is
            // released in its awaitClose, and firing Vosk before that finished
            // raced two owners for one mic (IOException "could not open
            // microphone", or a capture that hears nothing).
            wakeJob?.cancelAndJoin()
            try {
                var followUp = false
                while (isActive) {
                    val handled = captureAndRespond(followUp)
                    // Only keep the conversation open (no wake phrase needed for
                    // the next question) while hands-free is on.
                    if (!handled || !handsFreeActive()) break
                    followUp = true
                }
            } finally {
                // captureAndRespond already left the right resting state
                // (SPEAKING after an answer, IDLE after silence/failure); a
                // cancellation (mic button / onPause) may not have — clear the
                // mic flag, then bring hands-free listening back.
                if (_uiState.value.isListening) {
                    _uiState.value = _uiState.value.copy(
                        status = RoadMateStatus.IDLE,
                        isListening = false,
                    )
                }
                if (resumeWakeWord && wakeWordDesired) startWakeWordListening()
            }
        }
    }

    /**
     * One turn: open the mic, transcribe, answer. Returns true if a real
     * question was handled, false on silence / empty / failure — the caller
     * then stops the follow-up loop.
     *
     * [followUp] shortens the "nothing said" window (the driver is already in
     * a conversation) and keeps the previous answer on screen.
     */
    private suspend fun captureAndRespond(followUp: Boolean): Boolean = coroutineScope {
        _uiState.value = _uiState.value.copy(
            status = if (followUp) RoadMateStatus.FOLLOW_UP else RoadMateStatus.LISTENING,
            isListening = true,
            lastRecognizedInput = "",
            currentResponse = if (followUp) _uiState.value.currentResponse else "",
            isError = false,
        )

        var finalText = ""
        var failure: String? = null
        var heardSpeech = false

        val capture = launch {
            recordAudioUseCase()
                .catch { failure = SpokenText.SPEECH_FLOW_ERROR }
                .collect { event ->
                    when (event) {
                        is SpeechRecognitionEvent.Partial -> {
                            heardSpeech = true
                            _uiState.value = _uiState.value.copy(lastRecognizedInput = event.text)
                        }
                        is SpeechRecognitionEvent.Result -> finalText = event.text
                        is SpeechRecognitionEvent.Failed -> failure = event.message
                    }
                }
        }
        // Silence watchdog: if nothing is said within the window, stop waiting
        // and let the loop fall back to the wake phrase.
        val watchdog = launch {
            delay((if (followUp) FOLLOW_UP_SILENCE_MS else FIRST_SILENCE_MS).milliseconds)
            if (!heardSpeech) capture.cancel()
        }
        capture.join()
        watchdog.cancel()

        failure?.let { message ->
            _uiState.value = _uiState.value.copy(
                status = RoadMateStatus.IDLE,
                isListening = false,
                currentResponse = message,
                isError = true,
            )
            speechSynthesisRepository.speak(message)
            return@coroutineScope false
        }

        // Vosk returns bare lowercase text; punctuate an obvious question so it
        // reads right and the model gets a clearer signal.
        val recognized = QuestionPunctuation.normalize(finalText)
        if (recognized.isBlank()) {
            _uiState.value = _uiState.value.copy(
                status = RoadMateStatus.IDLE,
                isListening = false,
                lastRecognizedInput = "",
            )
            return@coroutineScope false
        }

        _uiState.value = _uiState.value.copy(
            status = RoadMateStatus.PROCESSING,
            lastRecognizedInput = recognized,
        )
        respondTo(recognized)
        speechSynthesisRepository.awaitDoneSpeaking()
        true
    }

    fun cancelListening() {
        listeningJob?.cancel()
        listeningJob = null
        // Cancelling the flow doesn't unqueue what TTS is already reading, so
        // tapping the mic to stop left the assistant talking over the driver
        // for the rest of a long answer.
        speechSynthesisRepository.stop()
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
                // Silence is the worst possible answer at 120 km/h: the driver
                // has no idea whether it heard them, is thinking, or died, and
                // the only way to find out is to look at the phone. Say
                // something.
                _uiState.value = _uiState.value.copy(
                    status = RoadMateStatus.IDLE,
                    isListening = false,
                    currentResponse = SpokenText.ANSWER_FAILED,
                    isError = true,
                )
                speechSynthesisRepository.speak(SpokenText.ANSWER_FAILED)
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
        val location = locationRepository.currentLocation()
        val weatherDescription = location?.let {
            weatherRepository.getCurrentWeatherDescription(it.latitude, it.longitude)
        }
        val calendar = Calendar.getInstance()
        return TravelContext(
            currentLocation = location,
            destination = null,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            date = calendar.time,
            userInput = userInput,
            weatherDescription = weatherDescription,
            placeLabel = currentPlaceRepository.label.value,
        )
    }

    override fun onCleared() {
        super.onCleared()
        cancelListening()
        stopAmbientListening()
        runCatching { wakeEarcon.release() }
    }

    private companion object {
        /** Give up waiting for the first question this long after "Sí, dime.". */
        const val FIRST_SILENCE_MS = 8_000L

        /** Shorter window for a follow-up — the driver's already talking to it. */
        const val FOLLOW_UP_SILENCE_MS = 6_000L
    }
}
