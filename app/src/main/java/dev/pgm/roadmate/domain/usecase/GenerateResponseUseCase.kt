package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.utils.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Builds a prompt from [TravelContext], asks Gemini Nano for a response, speaks
 * it aloud, and emits the response text for the UI to display.
 */
class GenerateResponseUseCase @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository
) {
    operator fun invoke(context: TravelContext, userInput: String): Flow<String> = flow {
        val prompt = PromptBuilder.buildPrompt(context, userInput)
        val response = geminiRepository.getResponse(prompt)
        speechSynthesisRepository.speak(response)
        emit(response)
    }.flowOn(Dispatchers.IO)
}
