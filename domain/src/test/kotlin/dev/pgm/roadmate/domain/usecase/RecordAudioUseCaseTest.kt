package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeSpeechRecognitionRepository
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAudioUseCaseTest {

    @Test
    fun `streams the repository's partials then the final result`() = runTest {
        val repository = FakeSpeechRecognitionRepository(result = "hola roadmate")
        val useCase = RecordAudioUseCase(repository)

        val events = useCase().toList()

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
        val useCase = RecordAudioUseCase(repository)

        assertEquals("para en la próxima", useCase.finalText())
    }

    @Test
    fun `finalText is empty when nothing is recognized`() = runTest {
        val useCase = RecordAudioUseCase(FakeSpeechRecognitionRepository(result = ""))

        assertEquals("", useCase.finalText())
    }
}
