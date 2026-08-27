package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeGeminiRepository
import dev.pgm.roadmate.domain.fake.FakeMapSearchRepository
import dev.pgm.roadmate.domain.fake.FakePhoneCallRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.ContactMatch
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
        mapSearchRepository: FakeMapSearchRepository = FakeMapSearchRepository()
    ) = GenerateResponseUseCase(geminiRepository, speechSynthesisRepository, phoneCallRepository, mapSearchRepository)

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
}
