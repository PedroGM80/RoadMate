package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeSpeechRecognitionRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordAudioUseCaseTest {

    private fun useCase(
        recognition: FakeSpeechRecognitionRepository,
        synthesis: FakeSpeechSynthesisRepository = FakeSpeechSynthesisRepository(),
    ) = RecordAudioUseCase(recognition, synthesis)

    @Test
    fun `streams the repository's partials then the final result`() = runTest {
        val repository = FakeSpeechRecognitionRepository(result = "hola roadmate")

        val events = useCase(repository)().toList()

        assertEquals(
            listOf(
                SpeechRecognitionEvent.Partial("hola roadmate"),
                SpeechRecognitionEvent.Result("hola roadmate"),
            ),
            events,
        )
        assertEquals(1, repository.invocationCount)
    }

    @Test
    fun `finalText returns the last Result text`() = runTest {
        val repository = FakeSpeechRecognitionRepository(result = "para en la próxima")

        assertEquals("para en la próxima", useCase(repository).finalText())
    }

    @Test
    fun `finalText is empty when nothing is recognized`() = runTest {
        assertEquals("", useCase(FakeSpeechRecognitionRepository(result = "")).finalText())
    }

    @Test
    fun `silences any ongoing speech and waits for it before opening the mic`() = runTest {
        val synthesis = FakeSpeechSynthesisRepository().apply { setSpeaking(true) }

        useCase(FakeSpeechRecognitionRepository(result = "hola"), synthesis)().toList()

        assertTrue(synthesis.stopped)
        assertEquals(1, synthesis.awaitDoneCount)
    }
}
