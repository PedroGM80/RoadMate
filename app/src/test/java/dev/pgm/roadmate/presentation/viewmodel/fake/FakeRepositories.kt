package dev.pgm.roadmate.presentation.viewmodel.fake

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.RouteResult
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.GreetingRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Local, minimal fakes for RoadMateViewModel tests — not shared with
 * :domain's fakes (module-private test sources aren't visible across
 * modules without a testFixtures setup, which wasn't worth the extra
 * Gradle wiring for this small a set of doubles).
 */

class FakeLocationRepository(
    initial: Pair<Double, Double>? = null,
    private val fetchDelayMs: Long = 0L,
    private val fetchResult: Pair<Double, Double>? = initial
) : LocationRepository {
    private val _location = MutableStateFlow(initial)
    override val location: StateFlow<Pair<Double, Double>?> = _location

    var fetchCount = 0
        private set

    override suspend fun getCurrentCoordinates(forceRefresh: Boolean): Pair<Double, Double>? {
        fetchCount++
        if (fetchDelayMs > 0) delay(fetchDelayMs)
        if (fetchResult != null) _location.value = fetchResult
        return fetchResult
    }
}

class FakeWeatherRepository(private val description: String? = null) : WeatherRepository {
    override suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String? = description
}

class FakeGeminiRepository(
    private val response: String = "respuesta",
    initialLocalAiStatus: LocalAiStatus = LocalAiStatus.ReadyAicore
) : GeminiRepository {
    val localAiStatusFlow = MutableStateFlow(initialLocalAiStatus)
    var downloadRequestedCount = 0
        private set

    override suspend fun getResponse(prompt: String): String = response
    override suspend fun warmUp() = Unit
    override fun clearCache() = Unit
    override fun localAiStatus(): Flow<LocalAiStatus> = localAiStatusFlow
    override suspend fun requestLocalAiModelDownload() {
        downloadRequestedCount++
    }
}

class FakeSpeechSynthesisRepository : SpeechSynthesisRepository {
    val spoken = mutableListOf<String>()
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking

    fun setSpeaking(value: Boolean) { _isSpeaking.value = value }

    override fun speak(text: String, onDone: () -> Unit) {
        spoken.add(text)
        onDone()
    }
    override fun stop() { _isSpeaking.value = false }
    override suspend fun awaitDoneSpeaking() { _isSpeaking.value = false }
}

class FakeSpeechRecognitionRepository(
    private val events: List<SpeechRecognitionEvent> = listOf(SpeechRecognitionEvent.Result("")),
) : SpeechRecognitionRepository {

    constructor(result: String) : this(
        if (result.isBlank()) listOf(SpeechRecognitionEvent.Result(""))
        else listOf(SpeechRecognitionEvent.Partial(result), SpeechRecognitionEvent.Result(result))
    )

    override fun recognize(): Flow<SpeechRecognitionEvent> = events.asFlow()
}

class FakeSilenceDetectionRepository(
    private val events: Flow<SilenceEvent> = flowOf()
) : SilenceDetectionRepository {
    override fun observeSilence(durationMs: Long, thresholdDb: Double): Flow<SilenceEvent> = events
}

class FakeWakeWordRepository(
    private val available: Boolean = false,
) : WakeWordRepository {
    /** Push a detection from a test with [tryEmit]; re-collected each restart. */
    val emissions = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun isAvailable(): Boolean = available
    override fun detections(): Flow<Unit> = emissions
}

class FakePhoneCallRepository(
    private val hasPermission: Boolean = true,
    private val lookupResult: ContactLookupResult = ContactLookupResult.NotFound
) : PhoneCallRepository {
    var placedCallTo: String? = null
    override fun hasCallPermission(): Boolean = hasPermission
    override suspend fun findContactByName(name: String): ContactLookupResult = lookupResult
    override fun placeCall(phoneNumber: String) {
        placedCallTo = phoneNumber
    }
}

class FakeRoutingRepository(
    private val result: RouteResult? = RouteResult(
        points = listOf(40.0 to -3.7, 40.1 to -3.6),
        distanceMeters = 12_300,
        durationSeconds = 1_080,
    ),
) : RoutingRepository {
    val requests = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()
    override val dataStatus = MutableStateFlow<RoutingDataStatus>(RoutingDataStatus.Idle)
    override suspend fun route(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): RouteResult? {
        requests += from to to
        return result
    }
}

class FakeMapSearchCoordinator(private val offlineMapReady: Boolean = true) : MapSearchCoordinator {
    val submitted = mutableListOf<MapSearchRequest>()
    private val _requests = MutableSharedFlow<MapSearchRequest>(extraBufferCapacity = 8)
    override val requests: Flow<MapSearchRequest> = _requests
    override fun hasOfflineMap(): Boolean = offlineMapReady
    override suspend fun submit(request: MapSearchRequest) {
        submitted += request
        _requests.emit(request)
    }
}

class FakeMediaRepository(private val canLaunch: Boolean = true) : MediaRepository {
    var lastLaunchedApp: MediaApp? = null
    override fun launchMediaApp(app: MediaApp): Boolean {
        lastLaunchedApp = app
        return canLaunch
    }
}

class FakeAssistantPreferencesRepository : AssistantPreferencesRepository {
    private val styleFlow = MutableStateFlow(AnswerStyle.DEFAULT)
    override val answerStyle: StateFlow<AnswerStyle> = styleFlow
    override suspend fun setAnswerStyle(style: AnswerStyle) {
        styleFlow.value = style
    }

    private val themeFlow = MutableStateFlow(ThemePreference.DEFAULT)
    override val themePreference: StateFlow<ThemePreference> = themeFlow
    override suspend fun setThemePreference(preference: ThemePreference) {
        themeFlow.value = preference
    }
}

class FakeMemoryRepository : MemoryRepository {
    val recorded = mutableListOf<Exchange>()
    private val facts = mutableListOf<UserFact>()
    override suspend fun recordExchange(question: String, answer: String) {
        recorded += Exchange(question, answer)
    }
    override suspend fun recentExchanges(limit: Int): List<Exchange> = recorded.takeLast(limit)
    override suspend fun searchExchanges(term: String, limit: Int): List<Exchange> {
        val tokens = term.lowercase().split(Regex("\\W+")).filter { it.length >= 4 }
        return recorded.filter { e -> tokens.any { (e.question + " " + e.answer).lowercase().contains(it) } }.take(limit)
    }
    private val places = mutableListOf<String>()
    override suspend fun remember(fact: UserFact) {
        if (fact.key != null) facts.removeAll { it.type == fact.type && it.key == fact.key }
        facts += fact
    }
    override suspend fun rememberPlace(place: String) { places += place }
    override suspend fun facts(type: FactType): List<UserFact> = facts.filter { it.type == type }
    override suspend fun frequentPlaces(limit: Int): List<UserFact> =
        places.distinct().take(limit).map { UserFact(FactType.PLACE, value = it) }
    override suspend fun forget(type: FactType, valueContains: String?): Int {
        val gone = facts.filter { it.type == type }
        facts.removeAll(gone)
        return gone.size
    }
    override suspend fun clearAll() {
        recorded.clear(); facts.clear(); places.clear()
    }
}

class FakeGreetingRepository(private val shouldGreet: Boolean = false) : GreetingRepository {
    var markedGreetedCount = 0
        private set
    override suspend fun shouldGreetToday(): Boolean = shouldGreet
    override suspend fun markGreetedToday() {
        markedGreetedCount++
    }
}
