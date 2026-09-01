package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.MapSearchRepository
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.utils.CallIntentParser
import dev.pgm.roadmate.utils.JokeProvider
import dev.pgm.roadmate.utils.MapSearchIntentParser
import dev.pgm.roadmate.utils.MediaIntentParser
import dev.pgm.roadmate.utils.MemoryCommandParser
import dev.pgm.roadmate.utils.PlaceName
import dev.pgm.roadmate.utils.PromptBuilder
import dev.pgm.roadmate.utils.StylePreferenceParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 *  5. "respuestas cortas / normales / con más detalle" — persists an
 *     [AssistantPreferencesRepository] setting and acknowledges it; that
 *     setting then shapes every future Gemini answer's length.
 *  6. Memory management — "recuerda que…" / "prefiero…" / "olvida lo de…" /
 *     "¿qué sabes de mí?" (PREFERENCE facts), "esta es mi casa" / "aquí es
 *     mi trabajo" (HOME/WORK, from the current location), "X es mi hermano"
 *     (RELATIONSHIP, so "llama a mi hermano" then resolves to X). Everything
 *     stored is fed into future Gemini prompts.
 * All of these work identically whether or not this device has on-device AI,
 * unlike every other question, which falls back to GeminiNanoManager's
 * generic FALLBACK_RESPONSE in "modo básico".
 *
 * Real questions (the Gemini path) are also given the last few exchanges
 * from [MemoryRepository] for continuity, and the new question/answer pair
 * is written back to it. The shortcuts aren't remembered — they're actions,
 * not conversation.
 */
class GenerateResponseUseCase @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val phoneCallRepository: PhoneCallRepository,
    private val mapSearchRepository: MapSearchRepository,
    private val mediaRepository: MediaRepository,
    private val assistantPreferencesRepository: AssistantPreferencesRepository,
    private val memoryRepository: MemoryRepository
) {
    operator fun invoke(context: TravelContext, userInput: String): Flow<String> = flow {
        val contactName = CallIntentParser.extractContactName(userInput)
        val mapQuery = MapSearchIntentParser.extractSearchQuery(userInput)
        val mediaApp = MediaIntentParser.extractMediaApp(userInput)
        val styleChange = StylePreferenceParser.parse(userInput)
        val memoryCommand = MemoryCommandParser.parse(userInput)
        val response = when {
            contactName != null -> handleCallRequest(contactName)
            mapQuery != null -> handleMapSearch(mapQuery, context.currentLocation)
            mediaApp != null -> handleMediaRequest(mediaApp)
            JokeProvider.matchesJokeIntent(userInput) -> JokeProvider.randomJoke()
            styleChange != null -> handleStyleChange(styleChange)
            memoryCommand != null -> handleMemoryCommand(memoryCommand, context)
            else -> askGemini(context, userInput)
        }
        speechSynthesisRepository.speak(response)
        emit(response)
    }

    private suspend fun handleMemoryCommand(
        command: MemoryCommandParser.Command,
        context: TravelContext,
    ): String = when (command) {
        is MemoryCommandParser.Command.Remember -> {
            memoryRepository.remember(UserFact(FactType.PREFERENCE, value = command.value))
            "Anotado."
        }

        is MemoryCommandParser.Command.Forget -> {
            val dropped = memoryRepository.forget(FactType.PREFERENCE, command.match)
            if (dropped > 0) "Vale, lo olvido." else "No tenía nada apuntado sobre eso."
        }

        MemoryCommandParser.Command.Recall -> {
            val prefs = memoryRepository.facts(FactType.PREFERENCE)
            if (prefs.isEmpty()) "Aún no he aprendido nada sobre ti."
            else "Recuerdo esto: " + prefs.joinToString("; ") { it.value } + "."
        }

        MemoryCommandParser.Command.SetHome -> saveNamedLocation(FactType.HOME, "casa", context)
        MemoryCommandParser.Command.SetWork -> saveNamedLocation(FactType.WORK, "trabajo", context)

        is MemoryCommandParser.Command.SetRelationship -> {
            memoryRepository.remember(
                UserFact(FactType.RELATIONSHIP, key = command.relation, value = command.name)
            )
            "Vale, ${command.name} es tu ${command.relation}."
        }
    }

    private suspend fun saveNamedLocation(type: FactType, label: String, context: TravelContext): String {
        val here = context.currentLocation
            ?: return "No tengo tu ubicación ahora mismo. Dímelo cuando tenga señal."
        memoryRepository.forget(type)
        memoryRepository.remember(UserFact(type, value = "${here.first},${here.second}"))
        return "Guardado. Esta es tu $label."
    }

    private suspend fun askGemini(context: TravelContext, userInput: String): String {
        val prompt = PromptBuilder.buildPrompt(
            context = context,
            userInput = userInput,
            style = assistantPreferencesRepository.answerStyle.first(),
            recentExchanges = memoryRepository.recentExchanges(),
            driverPreferences = memoryRepository.facts(FactType.PREFERENCE).map { it.value },
            frequentPlaces = memoryRepository.frequentPlaces().map { it.value },
            home = memoryRepository.facts(FactType.HOME).firstOrNull()?.value,
            work = memoryRepository.facts(FactType.WORK).firstOrNull()?.value,
        )
        val answer = geminiRepository.getResponse(prompt)
        memoryRepository.recordExchange(userInput, answer)
        return answer
    }

    private suspend fun handleStyleChange(style: AnswerStyle): String {
        assistantPreferencesRepository.setAnswerStyle(style)
        return style.spokenAck
    }

    private suspend fun handleCallRequest(rawName: String): String {
        if (!phoneCallRepository.hasCallPermission()) {
            return "No puedo llamar sin permiso. Actívalo en ajustes: contactos y teléfono."
        }
        // "llama a mi hermano" → resolve the relationship to a real name first.
        val relation = Regex("""^mi\s+(\p{L}+)$""", RegexOption.IGNORE_CASE)
            .find(rawName.trim())?.groupValues?.get(1)?.lowercase()
        val contactName = if (relation != null && relation in MemoryCommandParser.RELATIONS) {
            memoryRepository.facts(FactType.RELATIONSHIP).firstOrNull { it.key == relation }?.value
                ?: return "No sé quién es tu $relation. Dime antes \"nombre es mi $relation\"."
        } else {
            rawName
        }
        return when (val result = phoneCallRepository.findContactByName(contactName)) {
            is ContactLookupResult.Found -> {
                phoneCallRepository.placeCall(result.contact.phoneNumber)
                "Llamando a ${result.contact.name}"
            }
            is ContactLookupResult.Ambiguous ->
                "Tienes varios contactos con ese nombre. Dime cuál."
            ContactLookupResult.NotFound ->
                "No encuentro a $contactName en tus contactos."
        }
    }

    private suspend fun handleMapSearch(query: String, location: Pair<Double, Double>?): String =
        if (mapSearchRepository.searchNearby(query, location)) {
            memoryRepository.rememberPlace(PlaceName.normalize(query))
            "Busco $query en el mapa."
        } else {
            "No hay ninguna app de mapas para buscar $query."
        }

    private fun handleMediaRequest(app: MediaApp): String =
        if (mediaRepository.launchMediaApp(app)) {
            "Abro ${app.displayName}."
        } else {
            "No puedo abrir ${app.displayName}. ¿La tienes instalada?"
        }
}
