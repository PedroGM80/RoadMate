package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeSpeechRecognitionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAudioUseCaseTest {

    @Test
    fun `delegates to the repository and returns its result`() = runTest {
        val repository = FakeSpeechRecognitionRepository(result = "hola roadmate")
        val useCase = RecordAudioUseCase(repository)

        val result = useCase()

        assertEquals("hola roadmate", result)
        assertEquals(1, repository.invocationCount)
    }

    @Test
    fun `returns an empty string when recognition fails or finds nothing`() = runTest {
        val repository = FakeSpeechRecognitionRepository(result = "")
        val useCase = RecordAudioUseCase(repository)

        assertEquals("", useCase())
    }
}
