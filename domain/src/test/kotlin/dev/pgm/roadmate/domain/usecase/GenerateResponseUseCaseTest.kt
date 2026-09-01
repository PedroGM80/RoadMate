package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeAssistantPreferencesRepository
import dev.pgm.roadmate.domain.fake.FakeGeminiRepository
import dev.pgm.roadmate.domain.fake.FakeMapSearchRepository
import dev.pgm.roadmate.domain.fake.FakeMediaRepository
import dev.pgm.roadmate.domain.fake.FakeMemoryRepository
import dev.pgm.roadmate.domain.fake.FakePhoneCallRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.utils.JokeProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GenerateResponseUseCaseTest {

    private val context = TravelContext(currentLocation = 36.46 to -6.19, hour = 12, date = Date(), userInput = "")

    private fun useCase(
        geminiRepository: FakeGeminiRepository = FakeGeminiRepository(),
        speechSynthesisRepository: FakeSpeechSynthesisRepository = FakeSpeechSynthesisRepository(),
        phoneCallRepository: FakePhoneCallRepository = FakePhoneCallRepository(),
        mapSearchRepository: FakeMapSearchRepository = FakeMapSearchRepository(),
        mediaRepository: FakeMediaRepository = FakeMediaRepository(),
        assistantPreferencesRepository: FakeAssistantPreferencesRepository = FakeAssistantPreferencesRepository(),
        memoryRepository: FakeMemoryRepository = FakeMemoryRepository()
    ) = GenerateResponseUseCase(
        geminiRepository,
        speechSynthesisRepository,
        phoneCallRepository,
        mapSearchRepository,
        mediaRepository,
        assistantPreferencesRepository,
        memoryRepository
    )

    @Test
    fun `builds a prompt, asks Gemini, speaks and emits the response`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "para en la próxima área")
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()

        val emitted = useCase(geminiRepository, speechSynthesisRepository)(context, "¿dónde paro?").toList()

        assertEquals(listOf("para en la próxima área"), emitted)
        assertEquals(1, geminiRepository.responseCount)
        assertTrue(geminiRepository.lastPrompt!!.contains("¿dónde paro?"))
        assertEquals("para en la próxima área", speechSynthesisRepository.lastSpoken)
    }

    @Test
    fun `joke requests are answered locally, bypassing Gemini entirely`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()

        val emitted = useCase(geminiRepository, speechSynthesisRepository)(context, "cuéntame un chiste").toList()

        assertEquals(0, geminiRepository.responseCount)
        assertTrue(JokeProvider.matchesJokeIntent("cuéntame un chiste"))
        assertEquals(1, emitted.size)
        assertEquals(emitted.first(), speechSynthesisRepository.lastSpoken)
    }

    @Test
    fun `call requests with a single match place the call and bypass Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()
        val phoneCallRepository = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Found(ContactMatch("Ana", "600111222"))
        )

        val emitted = useCase(geminiRepository, speechSynthesisRepository, phoneCallRepository)(context, "llama a Ana")
            .toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals("600111222", phoneCallRepository.placedCallTo)
        assertEquals(listOf("Llamando a Ana"), emitted)
        assertEquals("Llamando a Ana", speechSynthesisRepository.lastSpoken)
    }

    @Test
    fun `ambiguous call requests ask for clarification instead of guessing`() = runTest {
        val phoneCallRepository = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Ambiguous(
                listOf(ContactMatch("Ana García", "600111222"), ContactMatch("Ana López", "600333444"))
            )
        )

        val emitted = useCase(phoneCallRepository = phoneCallRepository)(context, "llama a Ana").toList()

        assertEquals(null, phoneCallRepository.placedCallTo)
        assertTrue(emitted.first().contains("varios contactos"))
    }

    @Test
    fun `call requests without permission explain instead of failing silently`() = runTest {
        val phoneCallRepository = FakePhoneCallRepository(hasPermission = false)

        val emitted = useCase(phoneCallRepository = phoneCallRepository)(context, "llama a Ana").toList()

        assertEquals(null, phoneCallRepository.placedCallTo)
        assertTrue(emitted.first().contains("permiso"))
    }

    @Test
    fun `map search requests are handed to the Maps app and bypass Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val mapSearchRepository = FakeMapSearchRepository()

        val emitted = useCase(geminiRepository, mapSearchRepository = mapSearchRepository)(
            context,
            "busca una gasolinera cerca"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals("una gasolinera", mapSearchRepository.lastQuery)
        assertEquals(36.46 to -6.19, mapSearchRepository.lastLocation)
        assertTrue(emitted.first().contains("una gasolinera"))
    }

    @Test
    fun `dónde hay map search requests are also recognized`() = runTest {
        val mapSearchRepository = FakeMapSearchRepository()

        useCase(mapSearchRepository = mapSearchRepository)(context, "dónde hay un hotel").toList()

        assertEquals("un hotel", mapSearchRepository.lastQuery)
    }

    @Test
    fun `a map search with no maps app installed says so instead of pretending`() = runTest {
        val mapSearchRepository = FakeMapSearchRepository(hasMapsApp = false)

        val emitted = useCase(mapSearchRepository = mapSearchRepository)(context, "busca una gasolinera")
            .toList()

        assertTrue(emitted.first().contains("ninguna app de mapas"))
    }

    @Test
    fun `media requests launch the app and bypass Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val mediaRepository = FakeMediaRepository()

        val emitted = useCase(geminiRepository, mediaRepository = mediaRepository)(
            context,
            "pon música en Spotify"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(MediaApp.SPOTIFY, mediaRepository.lastLaunchedApp)
        assertEquals(listOf("Abro Spotify."), emitted)
    }

    @Test
    fun `a media request for an app that is not installed explains instead of claiming success`() = runTest {
        val mediaRepository = FakeMediaRepository(canLaunch = false)

        val emitted = useCase(mediaRepository = mediaRepository)(context, "abre YouTube Music").toList()

        assertEquals(MediaApp.YOUTUBE_MUSIC, mediaRepository.lastLaunchedApp)
        assertTrue(emitted.first().contains("No puedo abrir"))
    }

    @Test
    fun `an answer-style command persists the preference, acks it, and skips Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val prefs = FakeAssistantPreferencesRepository()

        val emitted = useCase(geminiRepository, assistantPreferencesRepository = prefs)(
            context,
            "de ahora en adelante respuestas cortas"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(AnswerStyle.BRIEF, prefs.answerStyle.value)
        assertEquals(listOf(AnswerStyle.BRIEF.spokenAck), emitted)
    }

    @Test
    fun `the stored answer style is folded into the Gemini prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val prefs = FakeAssistantPreferencesRepository(initial = AnswerStyle.BRIEF)

        useCase(geminiRepository, assistantPreferencesRepository = prefs)(context, "¿cuánto queda?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains(AnswerStyle.BRIEF.promptInstruction))
    }

    @Test
    fun `a real question and its answer are written to memory`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "unos 40 minutos")
        val memory = FakeMemoryRepository()

        useCase(geminiRepository, memoryRepository = memory)(context, "¿cuánto queda a Cádiz?").toList()

        assertEquals(1, memory.recorded.size)
        assertEquals(Exchange("¿cuánto queda a Cádiz?", "unos 40 minutos"), memory.recorded.first())
    }

    @Test
    fun `recent exchanges from memory are folded into the prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val memory = FakeMemoryRepository(initial = listOf(Exchange("¿distancia a Cádiz?", "32 km")))

        useCase(geminiRepository, memoryRepository = memory)(context, "¿y en coche?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains("¿distancia a Cádiz?"))
        assertTrue(geminiRepository.lastPrompt!!.contains("32 km"))
    }

    @Test
    fun `shortcut answers are not written to memory`() = runTest {
        val memory = FakeMemoryRepository()

        useCase(memoryRepository = memory)(context, "cuéntame un chiste").toList()
        useCase(memoryRepository = memory)(context, "abre Spotify").toList()

        assertTrue(memory.recorded.isEmpty())
    }

    @Test
    fun `recuerda que stores a preference and skips Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val memory = FakeMemoryRepository()

        val emitted = useCase(geminiRepository, memoryRepository = memory)(
            context,
            "recuerda que no me gustan las autovías",
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(
            listOf(UserFact(FactType.PREFERENCE, value = "no me gustan las autovías")),
            memory.facts(FactType.PREFERENCE),
        )
        assertEquals(listOf("Anotado."), emitted)
    }

    @Test
    fun `stored preferences are folded into the Gemini prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val memory = FakeMemoryRepository(
            initialFacts = listOf(UserFact(FactType.PREFERENCE, value = "no me gustan las autovías")),
        )

        useCase(geminiRepository, memoryRepository = memory)(context, "¿por dónde voy?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains("no me gustan las autovías"))
    }

    @Test
    fun `que sabes de mi reads the stored preferences back`() = runTest {
        val memory = FakeMemoryRepository(
            initialFacts = listOf(UserFact(FactType.PREFERENCE, value = "prefiere las nacionales")),
        )

        val emitted = useCase(memoryRepository = memory)(context, "¿qué sabes de mí?").toList()

        assertTrue(emitted.first().contains("prefiere las nacionales"))
    }
}
