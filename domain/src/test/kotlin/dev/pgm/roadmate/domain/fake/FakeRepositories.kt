package dev.pgm.roadmate.domain.fake

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf

/** Hand-rolled test doubles — no mocking library is a project dependency. */

class FakeGeminiRepository(private val response: String = "respuesta de prueba") : GeminiRepository {
    var lastPrompt: String? = null
    var responseCount = 0
    var cacheClearedCount = 0
    var downloadRequestedCount = 0

    override suspend fun getResponse(prompt: String): String {
        lastPrompt = prompt
        responseCount++
        return response
    }

    override suspend fun warmUp() = Unit

    override fun clearCache() {
        cacheClearedCount++
    }

    override fun localAiStatus(): Flow<LocalAiStatus> = flowOf(LocalAiStatus.ReadyAicore)

    override suspend fun requestLocalAiModelDownload() {
        downloadRequestedCount++
    }
}

class FakeSpeechSynthesisRepository : SpeechSynthesisRepository {
    var lastSpoken: String? = null
    var stopped = false
    var awaitDoneCount = 0

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking

    /** Test hook: pretend TTS is (or isn't) mid-utterance. */
    fun setSpeaking(value: Boolean) { _isSpeaking.value = value }

    override fun speak(text: String, onDone: () -> Unit) {
        lastSpoken = text
        onDone()
    }

    override fun stop() {
        stopped = true
        _isSpeaking.value = false
    }

    override suspend fun awaitDoneSpeaking() {
        awaitDoneCount++
        _isSpeaking.value = false
    }
}

class FakeSpeechRecognitionRepository(
    private val events: List<SpeechRecognitionEvent> = listOf(SpeechRecognitionEvent.Result("")),
) : SpeechRecognitionRepository {
    var invocationCount = 0

    /** Convenience: emit some partials then a final Result. */
    constructor(result: String) : this(
        if (result.isBlank()) listOf(SpeechRecognitionEvent.Result(""))
        else listOf(SpeechRecognitionEvent.Partial(result), SpeechRecognitionEvent.Result(result))
    )

    override fun recognize(): Flow<SpeechRecognitionEvent> {
        invocationCount++
        return events.asFlow()
    }
}

class FakeSilenceDetectionRepository(
    private val events: Flow<SilenceEvent> = flowOf()
) : SilenceDetectionRepository {
    var lastDurationMs: Long? = null
    var lastThresholdDb: Double? = null

    override fun observeSilence(durationMs: Long, thresholdDb: Double): Flow<SilenceEvent> {
        lastDurationMs = durationMs
        lastThresholdDb = thresholdDb
        return events
    }
}

class FakeLocationRepository(
    initialLocation: Pair<Double, Double>? = null
) : LocationRepository {
    private val _location = MutableStateFlow(initialLocation)
    override val location: StateFlow<Pair<Double, Double>?> = _location

    override suspend fun getCurrentCoordinates(forceRefresh: Boolean): Pair<Double, Double>? = _location.value
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

class FakeMapSearchCoordinator(private val offlineMapReady: Boolean = true) : MapSearchCoordinator {
    val submitted = mutableListOf<MapSearchRequest>()
    val lastRequest: MapSearchRequest? get() = submitted.lastOrNull()

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

class FakeAssistantPreferencesRepository(
    initial: AnswerStyle = AnswerStyle.DEFAULT
) : AssistantPreferencesRepository {
    val styleFlow = MutableStateFlow(initial)
    override val answerStyle: StateFlow<AnswerStyle> = styleFlow

    override suspend fun setAnswerStyle(style: AnswerStyle) {
        styleFlow.value = style
    }

    val themeFlow = MutableStateFlow(ThemePreference.DEFAULT)
    override val themePreference: StateFlow<ThemePreference> = themeFlow
    override suspend fun setThemePreference(preference: ThemePreference) {
        themeFlow.value = preference
    }
}

class FakeMemoryRepository(
    initial: List<Exchange> = emptyList(),
    initialFacts: List<UserFact> = emptyList(),
) : MemoryRepository {
    val recorded = initial.toMutableList()
    val storedFacts = initialFacts.toMutableList()

    override suspend fun recordExchange(question: String, answer: String) {
        recorded += Exchange(question, answer)
    }

    override suspend fun recentExchanges(limit: Int): List<Exchange> =
        recorded.takeLast(limit)

    override suspend fun searchExchanges(term: String, limit: Int): List<Exchange> {
        val tokens = term.lowercase().split(Regex("\\W+")).filter { it.length >= 4 }
        if (tokens.isEmpty()) return emptyList()
        return recorded.asReversed()
            .filter { e -> tokens.any { (e.question + " " + e.answer).lowercase().contains(it) } }
            .take(limit)
    }

    private val placeHits = linkedMapOf<String, Int>()

    override suspend fun remember(fact: UserFact) {
        if (fact.key != null) storedFacts.removeAll { it.type == fact.type && it.key == fact.key }
        if (storedFacts.none { it.type == fact.type && it.value == fact.value }) storedFacts += fact
    }

    override suspend fun rememberPlace(place: String) {
        placeHits[place] = (placeHits[place] ?: 0) + 1
    }

    override suspend fun frequentPlaces(limit: Int): List<UserFact> =
        placeHits.entries.sortedByDescending { it.value }.take(limit)
            .map { UserFact(FactType.PLACE, value = it.key) }

    override suspend fun facts(type: FactType): List<UserFact> = storedFacts.filter { it.type == type }

    override suspend fun forget(type: FactType, valueContains: String?): Int {
        val gone = storedFacts.filter {
            it.type == type && (valueContains.isNullOrBlank() || it.value.contains(valueContains, ignoreCase = true))
        }
        storedFacts.removeAll(gone)
        return gone.size
    }

    override suspend fun clearAll() {
        recorded.clear()
        storedFacts.clear()
        placeHits.clear()
    }
}
