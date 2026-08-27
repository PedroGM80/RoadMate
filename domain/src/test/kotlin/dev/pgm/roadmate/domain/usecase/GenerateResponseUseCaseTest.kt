package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeGeminiRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.model.TravelContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GenerateResponseUseCaseTest {

    @Test
    fun `builds a prompt, asks Gemini, speaks and emits the response`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "para en la próxima área")
        val speechSynthesisRepository = FakeSpeechSynthesisRepository()
        val useCase = GenerateResponseUseCase(geminiRepository, speechSynthesisRepository)

        val context = TravelContext(
            currentLocation = 36.46 to -6.19,
            hour = 12,
            date = Date(),
            userInput = "¿dónde paro?"
        )

        val emitted = useCase(context, context.userInput).toList()

        assertEquals(listOf("para en la próxima área"), emitted)
        assertEquals(1, geminiRepository.responseCount)
        assertTrue(geminiRepository.lastPrompt!!.contains("¿dónde paro?"))
        assertEquals("para en la próxima área", speechSynthesisRepository.lastSpoken)
    }
}
