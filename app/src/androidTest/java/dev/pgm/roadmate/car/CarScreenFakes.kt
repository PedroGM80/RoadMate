package dev.pgm.roadmate.car

import dev.pgm.roadmate.domain.audio.PcmAudioSource
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.CalendarEvent
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.CalendarRepository
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.PcmTranscriber
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.ReminderRepository
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.presentation.map.OfflineMapController
import dev.pgm.roadmate.presentation.map.OfflineMapStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op test doubles for the car screens' constructor dependencies. Every
 * method returns the "nothing happened yet" value — enough to build a screen
 * and render its template, which is all [CarScreensTest] asks of them. The
 * app's own unit-test fakes live in `src/test` and aren't visible here.
 */

class FakeCarLocationRepository(
    fix: Pair<Double, Double>? = 36.46 to -6.19,
) : LocationRepository {
    override val location = MutableStateFlow(fix)
    override suspend fun getCurrentCoordinates(forceRefresh: Boolean) = location.value
}

class FakeCarWeatherRepository : WeatherRepository {
    override suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String? = null
    override suspend fun getWeatherDescriptionFor(placeName: String): String? = null
}

class FakeCarSpeechSynthesisRepository : SpeechSynthesisRepository {
    override val isSpeaking = MutableStateFlow(false)
    override fun speak(text: String, onDone: () -> Unit) = onDone()
    override fun stop() = Unit
    override suspend fun awaitDoneSpeaking() = Unit
}

class FakeCarCurrentPlaceRepository(label: String? = null) : CurrentPlaceRepository {
    override val label = MutableStateFlow(label)
    override fun update(label: String?) { this.label.value = label }
}

class FakeCarMapSearchCoordinator : MapSearchCoordinator {
    override val requests = emptyFlow<MapSearchRequest>()
    override fun hasOfflineMap() = true
    override suspend fun submit(request: MapSearchRequest) = Unit
}

class FakeCarPreferencesRepository : AssistantPreferencesRepository {
    override val answerStyle = flowOf(AnswerStyle.DEFAULT)
    override suspend fun setAnswerStyle(style: AnswerStyle) = Unit
    override val themePreference = flowOf(ThemePreference.DEFAULT)
    override suspend fun setThemePreference(preference: ThemePreference) = Unit
    override val handsFreeEnabled = flowOf(true)
    override suspend fun setHandsFreeEnabled(enabled: Boolean) = Unit
    override val localAiModelId = flowOf<String?>(null)
    override suspend fun setLocalAiModelId(id: String) = Unit
    override val speechRate = flowOf(1.0f)
    override suspend fun setSpeechRate(rate: Float) = Unit
}

class FakeCarGeminiRepository : GeminiRepository {
    override suspend fun getResponse(prompt: String) = ""
    override suspend fun warmUp() = Unit
    override fun clearCache() = Unit
    override fun localAiStatus() = flowOf<LocalAiStatus>(LocalAiStatus.ReadyAicore)
    override suspend fun requestLocalAiModelDownload() = Unit
}

class FakeCarRoutingRepository(
    private val result: dev.pgm.roadmate.domain.model.RouteResult? = null,
) : RoutingRepository {
    override val dataStatus: StateFlow<RoutingDataStatus> = MutableStateFlow(RoutingDataStatus.Idle)
    override suspend fun route(from: Pair<Double, Double>, to: Pair<Double, Double>) = result
}

class FakeCarPhoneCallRepository : PhoneCallRepository {
    override fun hasCallPermission() = true
    override suspend fun findContactByName(name: String) = ContactLookupResult.NotFound
    override fun placeCall(phoneNumber: String) = Unit
}

class FakeCarMediaRepository : MediaRepository {
    override fun launchMediaApp(app: MediaApp) = false
    override fun launchAnyMusicApp(): MediaApp? = null
}

class FakeCarMemoryRepository : MemoryRepository {
    override suspend fun recordExchange(question: String, answer: String) = Unit
    override suspend fun recentExchanges(limit: Int): List<Exchange> = emptyList()
    override suspend fun searchExchanges(term: String, limit: Int): List<Exchange> = emptyList()
    override suspend fun remember(fact: UserFact) = Unit
    override suspend fun rememberPlace(place: String) = Unit
    override suspend fun facts(type: FactType): List<UserFact> = emptyList()
    override suspend fun frequentPlaces(limit: Int): List<UserFact> = emptyList()
    override suspend fun clearAll() = Unit
    override suspend fun forget(type: FactType, valueContains: String?) = 0
}

class FakeCarReminderRepository : ReminderRepository {
    override suspend fun schedule(text: String, whenEpochMillis: Long) = Unit
}

class FakeCarCalendarRepository : CalendarRepository {
    override fun hasPermission() = false
    override suspend fun eventsBetween(fromMillis: Long, toMillis: Long): List<CalendarEvent> = emptyList()
}

class FakeCarPcmTranscriber : PcmTranscriber {
    override suspend fun transcribe(source: PcmAudioSource) = ""
}

class FakeCarOfflineMapController : OfflineMapController {
    override val status: StateFlow<OfflineMapStatus> = MutableStateFlow(OfflineMapStatus.Idle)
    override fun refresh() = Unit
    override fun download(
        styleUrl: String,
        bounds: org.maplibre.android.geometry.LatLngBounds,
        pixelRatio: Float,
    ) = Unit
    override fun deleteAll() = Unit
}

/** A real [GenerateResponseUseCase] wired entirely from the fakes above. */
fun fakeGenerateResponseUseCase(): GenerateResponseUseCase = GenerateResponseUseCase(
    geminiRepository = FakeCarGeminiRepository(),
    speechSynthesisRepository = FakeCarSpeechSynthesisRepository(),
    phoneCallRepository = FakeCarPhoneCallRepository(),
    mapSearchCoordinator = FakeCarMapSearchCoordinator(),
    mediaRepository = FakeCarMediaRepository(),
    assistantPreferencesRepository = FakeCarPreferencesRepository(),
    memoryRepository = FakeCarMemoryRepository(),
    weatherRepository = FakeCarWeatherRepository(),
    reminderRepository = FakeCarReminderRepository(),
    calendarRepository = FakeCarCalendarRepository(),
)
