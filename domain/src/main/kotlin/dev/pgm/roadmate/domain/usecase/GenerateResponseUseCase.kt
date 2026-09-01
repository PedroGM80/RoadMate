package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.MapSearchRepository
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.utils.CallIntentParser
import dev.pgm.roadmate.utils.JokeProvider
import dev.pgm.roadmate.utils.MapSearchIntentParser
import dev.pgm.roadmate.utils.MediaIntentParser
import dev.pgm.roadmate.utils.PromptBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Builds a prompt from [TravelContext], asks Gemini Nano for a response, speaks
 * it aloud, and emits the response text for the UI to display.
 *
 * Four local shortcuts are checked before ever touching Gemini, in this order:
 *  1. "llama a X" — placed directly (ACTION_CALL, no dial-pad confirmation,
 *     by design: hands-free while driving). Ambiguous/missing contacts get a
 *     spoken explanation instead of guessing who to call.
 *  2. "busca/encuentra X" — handed to the device's Maps app as a geo: search
 *     (gasolineras, hoteles, restaurantes...), not looked up by RoadMate
 *     itself, so the "your questions never leave the phone" promise still
 *     holds — the query only travels through the Maps app already on-device.
 *  3. "abre/pon Spotify|YouTube Music" — launches that music app. Just opens
 *     it (no playback control), and says so.
 *  4. Joke requests — answered from JokeProvider's local bank.
 * All four work identically whether or not this device has on-device AI,
 * unlike every other question, which falls back to GeminiNanoManager's
 * generic FALLBACK_RESPONSE in "modo básico".
 */
class GenerateResponseUseCase @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val phoneCallRepository: PhoneCallRepository,
    private val mapSearchRepository: MapSearchRepository,
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(context: TravelContext, userInput: String): Flow<String> = flow {
        val contactName = CallIntentParser.extractContactName(userInput)
        val mapQuery = MapSearchIntentParser.extractSearchQuery(userInput)
        val mediaApp = MediaIntentParser.extractMediaApp(userInput)
        val response = when {
            contactName != null -> handleCallRequest(contactName)
            mapQuery != null -> handleMapSearch(mapQuery, context.currentLocation)
            mediaApp != null -> handleMediaRequest(mediaApp)
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

    private fun handleMapSearch(query: String, location: Pair<Double, Double>?): String {
        mapSearchRepository.searchNearby(query, location)
        return "Buscando $query en el mapa"
    }

    private fun handleMediaRequest(app: MediaApp): String =
        if (mediaRepository.launchMediaApp(app)) {
            "Abriendo ${app.displayName}"
        } else {
            "No he podido abrir ${app.displayName}. ¿La tienes instalada?"
        }
}
