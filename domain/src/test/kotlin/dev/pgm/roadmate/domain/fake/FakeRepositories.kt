package dev.pgm.roadmate.domain.fake

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.SilenceEvent
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Hand-rolled test doubles — no mocking library is a project dependency. */

class FakeGeminiRepository(private val response: String = "respuesta de prueba") : GeminiRepository {
    var lastPrompt: String? = null
    var responseCount = 0
    var cacheClearedCount = 0

    override suspend fun getResponse(prompt: String): String {
        lastPrompt = prompt
        responseCount++
        return response
    }

    override fun clearCache() {
        cacheClearedCount++
    }

    override suspend fun isLocalAiAvailable(): Boolean = true
}

class FakeSpeechSynthesisRepository : SpeechSynthesisRepository {
    var lastSpoken: String? = null
    var stopped = false

    override fun speak(text: String, onDone: () -> Unit) {
        lastSpoken = text
        onDone()
    }

    override fun stop() {
        stopped = true
    }
}

class FakeSpeechRecognitionRepository(private val result: String = "") : SpeechRecognitionRepository {
    var invocationCount = 0

    override suspend fun recognizeSpeech(): String {
        invocationCount++
        return result
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
