package dev.pgm.roadmate.presentation.viewmodel.fake

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val localAiAvailable: Boolean = true
) : GeminiRepository {
    override suspend fun getResponse(prompt: String): String = response
    override fun clearCache() = Unit
    override suspend fun isLocalAiAvailable(): Boolean = localAiAvailable
}

class FakeSpeechSynthesisRepository : SpeechSynthesisRepository {
    val spoken = mutableListOf<String>()
    override fun speak(text: String, onDone: () -> Unit) {
        spoken.add(text)
        onDone()
    }
    override fun stop() = Unit
}

class FakeSpeechRecognitionRepository(private val result: String = "") : SpeechRecognitionRepository {
    override suspend fun recognizeSpeech(): String = result
}

class FakeSilenceDetectionRepository(
    private val events: Flow<SilenceEvent> = flowOf()
) : SilenceDetectionRepository {
    override fun observeSilence(durationMs: Long, thresholdDb: Double): Flow<SilenceEvent> = events
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
