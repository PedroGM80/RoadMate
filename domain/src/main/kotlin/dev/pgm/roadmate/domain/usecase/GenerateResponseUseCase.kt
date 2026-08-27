package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.utils.CallIntentParser
import dev.pgm.roadmate.utils.JokeProvider
import dev.pgm.roadmate.utils.PromptBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Builds a prompt from [TravelContext], asks Gemini Nano for a response, speaks
 * it aloud, and emits the response text for the UI to display.
 *
 * Two local shortcuts are checked before ever touching Gemini, in this order:
 *  1. "llama a X" — placed directly (ACTION_CALL, no dial-pad confirmation,
 *     by design: hands-free while driving). Ambiguous/missing contacts get a
 *     spoken explanation instead of guessing who to call.
 *  2. Joke requests — answered from JokeProvider's local bank.
 * Both work identically whether or not this device has on-device AI, unlike
 * every other question, which falls back to GeminiNanoManager's generic
 * FALLBACK_RESPONSE in "modo básico".
 */
class GenerateResponseUseCase @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val phoneCallRepository: PhoneCallRepository
) {
    operator fun invoke(context: TravelContext, userInput: String): Flow<String> = flow {
        val contactName = CallIntentParser.extractContactName(userInput)
        val response = when {
            contactName != null -> handleCallRequest(contactName)
            JokeProvider.matchesJokeIntent(userInput) -> JokeProvider.randomJoke()
            else -> geminiRepository.getResponse(PromptBuilder.buildPrompt(context, userInput))
        }
        speechSynthesisRepository.speak(response)
        emit(response)
    }

    private suspend fun handleCallRequest(contactName: String): String {
        if (!phoneCallRepository.hasCallPermission()) {
            return "No tengo permiso para llamar. Concede el permiso de contactos y teléfono en los ajustes de RoadMate."
        }
        return when (val result = phoneCallRepository.findContactByName(contactName)) {
            is ContactLookupResult.Found -> {
                phoneCallRepository.placeCall(result.contact.phoneNumber)
                "Llamando a ${result.contact.name}"
            }
            is ContactLookupResult.Ambiguous ->
                "Hay varios contactos llamados $contactName. Sé más específico."
            ContactLookupResult.NotFound ->
                "No encuentro ningún contacto llamado $contactName."
        }
    }
}
