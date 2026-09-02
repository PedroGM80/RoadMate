package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.MessagingRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.ReminderRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.utils.ArithmeticParser
import dev.pgm.roadmate.utils.CallFollowUpParser
import dev.pgm.roadmate.utils.CallIntentParser
import dev.pgm.roadmate.utils.JokeProvider
import dev.pgm.roadmate.utils.LocationQuestionParser
import dev.pgm.roadmate.utils.MapSearchIntentParser
import dev.pgm.roadmate.utils.MediaIntentParser
import dev.pgm.roadmate.utils.MessageIntentParser
import dev.pgm.roadmate.utils.PlaceCategoryParser
import dev.pgm.roadmate.utils.MemoryCommandParser
import dev.pgm.roadmate.utils.ParkingIntentParser
import dev.pgm.roadmate.utils.PlaybackCommandParser
import dev.pgm.roadmate.utils.ReminderIntentParser
import dev.pgm.roadmate.utils.PlaceName
import dev.pgm.roadmate.utils.PromptBuilder
import dev.pgm.roadmate.utils.SentenceChunker
import dev.pgm.roadmate.utils.spanishRegex
import dev.pgm.roadmate.utils.SpokenText
import dev.pgm.roadmate.utils.StylePreferenceParser
import dev.pgm.roadmate.utils.UnitConversionParser
import dev.pgm.roadmate.utils.WeatherIntentParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.Locale
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Speech-rate bounds and per-nudge step for "más despacio" / "más rápido". */
private const val MIN_RATE = 0.6f
private const val MAX_RATE = 1.6f
private const val RATE_STEP = 0.15f

private val SPANISH_ES: Locale = Locale.forLanguageTag("es-ES")
private val COMPASS_ES = listOf(
    "norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste",
)

/**
 * Builds a prompt from [TravelContext], asks the on-device model for a
 * response, speaks it aloud, and emits the response text for the UI to
 * display. Real questions are streamed: the answer is spoken sentence by
 * sentence as the model produces it (see [streamGeminiAnswer]) rather than
 * after the whole reply, and each emission carries the cumulative text.
 *
 * Local shortcuts are checked before ever touching Gemini, in this order:
 *  1. "llama a X" — placed directly (ACTION_CALL, no dial-pad confirmation,
 *     by design: hands-free while driving). Ambiguous/missing contacts get a
 *     spoken explanation instead of guessing who to call.
 *  2. "busca/encuentra X" — shown on RoadMate's own downloaded offline map,
 *     never an external Maps app. A category ("gasolineras", "hoteles",
 *     "restaurantes") becomes a POI filter; anything else is matched by name
 *     against the downloaded tiles. With no region downloaded, RoadMate says
 *     so instead of falling back to another app.
 *  3. "abre/pon Spotify|YouTube Music" — launches that music app. Just opens
 *     it (no playback control), and says so. A bare "pon música" that names
 *     no app opens whichever known one is installed.
 *  4. Joke requests — answered from JokeProvider's local bank.
 *  5. "respuestas cortas / normales / con más detalle" — persists an
 *     [AssistantPreferencesRepository] setting and acknowledges it; that
 *     setting then shapes every future Gemini answer's length.
 *  6. Memory management — "recuerda que…" / "prefiero…" / "olvida lo de…" /
 *     "¿qué sabes de mí?" (PREFERENCE facts), "esta es mi casa" / "aquí es
 *     mi trabajo" (HOME/WORK, from the current location), "X es mi hermano"
 *     (RELATIONSHIP, so "llama a mi hermano" then resolves to X). Everything
 *     stored is fed into future Gemini prompts.
 *  7. "¿qué tiempo hace?" / "¿va a llover?" — answered straight from the
 *     weather already in [TravelContext], not by the model (which often
 *     can't, and in "modo básico" there's no model). Plain "no puedo
 *     consultar el tiempo" when there's no fix / network / API key.
 * All of these work identically whether or not this device has on-device AI,
 * unlike every other question, which falls back to GeminiNanoManager's
 * generic FALLBACK_RESPONSE in "modo básico".
 *
 * Real questions (the Gemini path) are also given the last few exchanges
 * from [MemoryRepository] for continuity, and the new question/answer pair
 * is written back to it. The shortcuts aren't remembered — they're actions,
 * not conversation.
 *
 * Holds one bit of state, [pendingCall]: when "llama a X" is ambiguous the
 * candidate list is kept so the next utterance ("la segunda", "García") can
 * finish the call. Per-instance, which is exactly the voice loop's scope.
 */
class GenerateResponseUseCase @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val phoneCallRepository: PhoneCallRepository,
    private val mapSearchCoordinator: MapSearchCoordinator,
    private val mediaRepository: MediaRepository,
    private val assistantPreferencesRepository: AssistantPreferencesRepository,
    private val memoryRepository: MemoryRepository,
    private val weatherRepository: WeatherRepository,
    private val messagingRepository: MessagingRepository,
    private val reminderRepository: ReminderRepository
) {
    /** The candidates from an unresolved "llama a X" — awaiting "la segunda" etc. */
    private var pendingCall: List<ContactMatch>? = null

    /** The last thing RoadMate said, so "repite" can say it again. */
    private var lastAnswer: String? = null

    operator fun invoke(context: TravelContext, userInput: String): Flow<String> = flow {
        pendingCall?.let { pending ->
            val picked = CallFollowUpParser.resolve(userInput, pending)
            pendingCall = null
            if (picked != null) {
                val followUp = placeResolvedCall(picked)
                lastAnswer = followUp
                speechSynthesisRepository.speak(followUp)
                emit(followUp)
                return@flow
            }
            // not a follow-up — fall through to normal handling
        }

        // "repite" / "más despacio" — controls how RoadMate speaks, not a question.
        PlaybackCommandParser.parse(userInput)?.let { command ->
            val reply = handlePlaybackCommand(command)
            speechSynthesisRepository.speak(reply)
            emit(reply)
            return@flow
        }

        val contactName = CallIntentParser.extractContactName(userInput)
        val mapQuery = MapSearchIntentParser.extractSearchQuery(userInput)
        val mediaApp = MediaIntentParser.extractMediaApp(userInput)
        val styleChange = StylePreferenceParser.parse(userInput)
        val memoryCommand = MemoryCommandParser.parse(userInput)
        val arithmetic = ArithmeticParser.evaluate(userInput)
        val conversion = UnitConversionParser.convert(userInput)
        val parkingIntent = ParkingIntentParser.parse(userInput)
        val messageRequest = MessageIntentParser.parse(userInput)
        val reminder = ReminderIntentParser.parse(userInput)
        val shortcut = when {
            // Before the map/call parsers — "llévame al coche" and "dónde está
            // el coche" would otherwise be read as a place search.
            parkingIntent != null -> handleParking(parkingIntent, context)
            messageRequest != null -> handleMessage(messageRequest)
            reminder != null -> handleReminder(reminder, context)
            // "¿dónde estoy?" — answer from the map if it has a street resolved,
            // otherwise fall through to the model (it has the coordinates).
            LocationQuestionParser.matches(userInput) && !context.placeLabel.isNullOrBlank() ->
                SpokenText.youAreAt(context.placeLabel!!.replace(" · ", ", "))
            contactName != null -> handleCallRequest(contactName)
            mapQuery != null -> handleMapSearch(
                mapQuery,
                context.currentLocation,
                navigate = MapSearchIntentParser.isNavigationRequest(userInput),
            )
            mediaApp != null -> handleMediaRequest(mediaApp)
            MediaIntentParser.wantsMusicWithoutApp(userInput) -> handleAnyMusicRequest()
            JokeProvider.matchesJokeIntent(userInput) -> JokeProvider.randomJoke()
            styleChange != null -> handleStyleChange(styleChange)
            memoryCommand != null -> handleMemoryCommand(memoryCommand, context)
            WeatherIntentParser.isWeatherQuestion(userInput) -> handleWeather(context, userInput)
            conversion != null -> conversion
            arithmetic != null -> arithmetic
            else -> null
        }

        if (shortcut != null) {
            // Shortcuts are fixed, one-line replies — speak and emit as one.
            lastAnswer = shortcut
            speechSynthesisRepository.speak(shortcut)
            emit(shortcut)
            return@flow
        }

        // A real question: stream the model's answer, speaking each sentence
        // the moment it lands instead of after the whole reply.
        streamGeminiAnswer(context, userInput)
    }

    /**
     * Collects the streamed answer, speaking every sentence as soon as it is
     * complete so TTS starts within a second or two rather than after the full
     * generation. The UI still gets the cumulative text on each emission, and
     * the finished answer is written to memory as a single exchange.
     */
    private suspend fun FlowCollector<String>.streamGeminiAnswer(
        context: TravelContext,
        userInput: String,
    ) {
        val prompt = buildGeminiPrompt(context, userInput)
        val chunker = SentenceChunker()
        var full = ""
        geminiRepository.getResponseStream(prompt).collect { cumulative ->
            full = cumulative
            chunker.consume(cumulative).forEach { speechSynthesisRepository.speak(it) }
            emit(cumulative)
        }
        chunker.flush()?.let { speechSynthesisRepository.speak(it) }
        if (full.isNotBlank()) {
            lastAnswer = full
            memoryRepository.recordExchange(userInput, full)
        }
    }

    /**
     * "repite" says the last answer again; "más despacio" / "más rápido" /
     * "voz normal" nudge the speech rate and persist it. All spoken and shown
     * like any other one-line reply.
     */
    private suspend fun handlePlaybackCommand(command: PlaybackCommandParser.Command): String {
        if (command == PlaybackCommandParser.Command.REPEAT) {
            return lastAnswer ?: SpokenText.NOTHING_TO_REPEAT
        }
        val current = assistantPreferencesRepository.speechRate.first()
        val target = when (command) {
            PlaybackCommandParser.Command.SLOWER -> current - RATE_STEP
            PlaybackCommandParser.Command.FASTER -> current + RATE_STEP
            PlaybackCommandParser.Command.NORMAL_SPEED -> 1.0f
            PlaybackCommandParser.Command.REPEAT -> current // unreachable
        }.coerceIn(MIN_RATE, MAX_RATE)

        if (target == current && command != PlaybackCommandParser.Command.NORMAL_SPEED) {
            return SpokenText.SPEECH_RATE_LIMIT
        }
        assistantPreferencesRepository.setSpeechRate(target)
        speechSynthesisRepository.setSpeechRate(target)
        return when (command) {
            PlaybackCommandParser.Command.SLOWER -> SpokenText.SPEECH_SLOWER
            PlaybackCommandParser.Command.FASTER -> SpokenText.SPEECH_FASTER
            else -> SpokenText.SPEECH_NORMAL_SPEED
        }
    }

    private suspend fun buildGeminiPrompt(context: TravelContext, userInput: String): String =
        coroutineScope {
            // Independent reads — fan them out so the prompt isn't gated by six
            // sequential DataStore/Room round-trips on the answer's critical path.
            val style = async { assistantPreferencesRepository.answerStyle.first() }
            val recent = async { memoryRepository.recentExchanges(limit = 1) }
            val preferences = async { memoryRepository.facts(FactType.PREFERENCE).map { it.value } }
            val places = async { memoryRepository.frequentPlaces().map { it.value } }
            val home = async { memoryRepository.facts(FactType.HOME).firstOrNull()?.value }
            val work = async { memoryRepository.facts(FactType.WORK).firstOrNull()?.value }
            PromptBuilder.buildPrompt(
                context = context,
                userInput = userInput,
                style = style.await(),
                recentExchanges = recent.await(),
                driverPreferences = preferences.await(),
                frequentPlaces = places.await(),
                home = home.await(),
                work = work.await(),
            )
        }

    private suspend fun handleMemoryCommand(
        command: MemoryCommandParser.Command,
        context: TravelContext,
    ): String = when (command) {
        is MemoryCommandParser.Command.Remember -> {
            memoryRepository.remember(UserFact(FactType.PREFERENCE, value = command.value))
            SpokenText.NOTED
        }

        is MemoryCommandParser.Command.Forget -> {
            val dropped = memoryRepository.forget(FactType.PREFERENCE, command.match)
            if (dropped > 0) SpokenText.FORGOTTEN else SpokenText.NOTHING_TO_FORGET
        }

        MemoryCommandParser.Command.Recall -> {
            val prefs = memoryRepository.facts(FactType.PREFERENCE)
            if (prefs.isEmpty()) SpokenText.NOTHING_LEARNED
            else SpokenText.known(prefs.joinToString("; ") { it.value })
        }

        is MemoryCommandParser.Command.Search -> {
            val hit = memoryRepository.searchExchanges(command.term).firstOrNull()
            if (hit == null) SpokenText.NOTHING_ON_RECORD
            else SpokenText.recalled(hit.question, hit.answer)
        }

        MemoryCommandParser.Command.SetHome -> saveNamedLocation(FactType.HOME, "casa", context)
        MemoryCommandParser.Command.SetWork -> saveNamedLocation(FactType.WORK, "trabajo", context)

        is MemoryCommandParser.Command.SetRelationship -> {
            memoryRepository.remember(
                UserFact(FactType.RELATIONSHIP, key = command.relation, value = command.name)
            )
            SpokenText.relationSaved(command.name, command.relation)
        }
    }

    private suspend fun saveNamedLocation(type: FactType, label: String, context: TravelContext): String {
        val here = context.currentLocation ?: return SpokenText.NO_LOCATION_YET
        memoryRepository.forget(type)
        memoryRepository.remember(UserFact(type, value = "${here.first},${here.second}"))
        return SpokenText.locationSaved(label)
    }

    private suspend fun handleStyleChange(style: AnswerStyle): String {
        assistantPreferencesRepository.setAnswerStyle(style)
        return style.spokenAck
    }

    private suspend fun handleCallRequest(rawName: String): String {
        if (!phoneCallRepository.hasCallPermission()) {
            return SpokenText.CALL_NO_PERMISSION
        }
        // "llama a mi hermano" → resolve the relationship to a real name first.
        val relation = spanishRegex("""^mi\s+(\p{L}+)$""")
            .find(rawName.trim())?.groupValues?.get(1)?.lowercase()
        val contactName = if (relation != null && relation in MemoryCommandParser.RELATIONS) {
            memoryRepository.facts(FactType.RELATIONSHIP).firstOrNull { it.key == relation }?.value
                ?: return SpokenText.unknownRelation(relation)
        } else {
            rawName
        }
        return when (val result = phoneCallRepository.findContactByName(contactName)) {
            is ContactLookupResult.Found -> {
                phoneCallRepository.placeCall(result.contact.phoneNumber)
                SpokenText.calling(result.contact.name)
            }
            is ContactLookupResult.Ambiguous -> {
                pendingCall = result.matches // resolved by the next "la segunda" / "la de trabajo"
                val oneContact = result.matches.map { it.name }.distinct().size == 1
                if (oneContact) {
                    SpokenText.callWhichNumber(
                        result.matches.first().name,
                        result.matches.map { it.spokenLabel },
                    )
                } else {
                    SpokenText.CALL_AMBIGUOUS
                }
            }
            ContactLookupResult.NotFound -> SpokenText.contactNotFound(contactName)
        }
    }

    /**
     * "dile a Ana que llego" — resolve the recipient in contacts (same lookup
     * as calls) and hand the text to [MessagingRepository] as an SMS. An
     * ambiguous name is not sent: safer to ask again than to text the wrong
     * person.
     */
    private suspend fun handleMessage(request: MessageIntentParser.Request): String {
        if (!messagingRepository.hasSmsPermission()) return SpokenText.SMS_NO_PERMISSION

        return when (val result = phoneCallRepository.findContactByName(request.recipient)) {
            is ContactLookupResult.Found -> {
                val ok = messagingRepository.sendSms(result.contact.phoneNumber, request.body)
                if (ok) SpokenText.messageSent(result.contact.name) else SpokenText.MESSAGE_FAILED
            }
            is ContactLookupResult.Ambiguous -> SpokenText.CALL_AMBIGUOUS
            ContactLookupResult.NotFound -> SpokenText.contactNotFound(request.recipient)
        }
    }

    /**
     * "recuérdame X en media hora / a las 6" — schedules a local alarm and
     * confirms when it will fire. A clock time already gone today rolls to
     * this evening, then to tomorrow.
     */
    private suspend fun handleReminder(
        r: ReminderIntentParser.Reminder,
        context: TravelContext,
    ): String {
        val now = context.date.time
        val whenMillis: Long
        val phrase: String
        if (r.delayMinutes != null) {
            whenMillis = now + r.delayMinutes * 60_000L
            phrase = when {
                r.delayMinutes < 60 -> "en ${r.delayMinutes} minutos"
                r.delayMinutes == 60 -> "en una hora"
                r.delayMinutes == 90 -> "en una hora y media"
                else -> "en ${r.delayMinutes / 60} horas"
            }
        } else {
            val atHour = r.atHour ?: return SpokenText.ANSWER_FAILED
            val cal = java.util.Calendar.getInstance().apply {
                time = context.date
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                set(java.util.Calendar.HOUR_OF_DAY, atHour)
                set(java.util.Calendar.MINUTE, r.atMinute)
            }
            if (cal.timeInMillis <= now + 30_000L) {
                if (atHour < 12) cal.add(java.util.Calendar.HOUR_OF_DAY, 12)
                if (cal.timeInMillis <= now + 30_000L) cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            whenMillis = cal.timeInMillis
            phrase = "a las %02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE),
            )
        }
        reminderRepository.schedule(r.text, whenMillis)
        return SpokenText.reminderSet(phrase)
    }

    private fun placeResolvedCall(match: ContactMatch): String {
        if (!phoneCallRepository.hasCallPermission()) return SpokenText.CALL_NO_PERMISSION
        phoneCallRepository.placeCall(match.phoneNumber)
        return SpokenText.calling(match.name)
    }

    /**
     * "¿qué tiempo hace?" — for here, straight from the weather already
     * fetched into [TravelContext] (no model call). For "…en <sitio>",
     * fetch that place on demand. Say so plainly when it's unavailable
     * (no fix, no network, no `OPENWEATHER_API_KEY`) rather than guess.
     */
    private suspend fun handleWeather(context: TravelContext, userInput: String): String {
        val place = WeatherIntentParser.placeIn(userInput)
        if (place != null) {
            val elsewhere = weatherRepository.getWeatherDescriptionFor(place)
            return if (!elsewhere.isNullOrBlank()) SpokenText.weatherAt(place, elsewhere)
            else SpokenText.weatherUnavailableFor(place)
        }
        val description = context.weatherDescription
        return if (!description.isNullOrBlank()) SpokenText.weatherNow(description)
        else SpokenText.WEATHER_UNAVAILABLE
    }

    /**
     * Routes the query to the in-app offline map. A recognised category
     * becomes a POI filter; otherwise it's a name to match against the
     * downloaded tiles. Nothing is downloaded → say so; there is no
     * external-Maps fallback.
     */
    private suspend fun handleMapSearch(
        query: String,
        location: Pair<Double, Double>?,
        navigate: Boolean,
    ): String {
        if (!mapSearchCoordinator.hasOfflineMap()) return SpokenText.NO_OFFLINE_MAP

        // "llévame a casa / al trabajo" — route straight to the saved
        // coordinate instead of searching the tiles for a place named "casa".
        if (navigate) {
            savedPlaceFor(query)?.let { (type, label) ->
                val coords = memoryRepository.facts(type).firstOrNull()?.value?.toCoords()
                    ?: return SpokenText.placeNotSet(label)
                mapSearchCoordinator.submit(
                    MapSearchRequest(
                        rawQuery = label,
                        category = null,
                        origin = location,
                        navigate = true,
                        destination = coords,
                    ),
                )
                return SpokenText.navigatingTo(label)
            }
        }

        val category = PlaceCategoryParser.parse(query)
        mapSearchCoordinator.submit(
            MapSearchRequest(
                rawQuery = query,
                category = category,
                origin = location,
                navigate = navigate,
            ),
        )
        memoryRepository.rememberPlace(PlaceName.normalize(query))
        return when {
            navigate -> SpokenText.navigatingTo(query)
            category != null -> SpokenText.showingCategoryOnMap(query)
            else -> SpokenText.searchingMap(query)
        }
    }

    /** "casa" / "mi trabajo" / "la oficina" → the memory fact to route to. */
    private fun savedPlaceFor(query: String): Pair<FactType, String>? {
        val q = query.trim().lowercase()
        return when {
            spanishRegex("""^(?:mi\s+|a\s+|hacia\s+)?casa$""").matches(q) -> FactType.HOME to "casa"
            spanishRegex("""^(?:mi\s+|al\s+|el\s+|la\s+)?(?:trabajo|oficina|curro)$""").matches(q) ->
                FactType.WORK to "trabajo"
            else -> null
        }
    }

    /**
     * "he aparcado aquí" stores the current fix (replacing any previous one);
     * "¿dónde aparqué?" / "llévame al coche" give the distance + compass
     * bearing to it, and TAKE_ME also drops a route on the offline map.
     */
    private suspend fun handleParking(
        intent: ParkingIntentParser.Intent,
        context: TravelContext,
    ): String {
        val here = context.currentLocation

        if (intent == ParkingIntentParser.Intent.SAVE) {
            if (here == null) return SpokenText.PARKING_NO_FIX_TO_SAVE
            memoryRepository.forget(FactType.PARKING)
            memoryRepository.remember(UserFact(FactType.PARKING, value = "${here.first},${here.second}"))
            return SpokenText.PARKING_SAVED
        }

        val spot = memoryRepository.facts(FactType.PARKING).firstOrNull()?.value?.toCoords()
            ?: return SpokenText.PARKING_NONE
        if (here == null) return SpokenText.PARKING_NO_FIX_NOW

        val distance = distanceWords(here, spot)
        val direction = COMPASS_ES[bearingIndex(here, spot)]

        return if (intent == ParkingIntentParser.Intent.TAKE_ME && mapSearchCoordinator.hasOfflineMap()) {
            mapSearchCoordinator.submit(
                MapSearchRequest(
                    rawQuery = "el coche",
                    category = null,
                    origin = here,
                    navigate = true,
                    destination = spot,
                ),
            )
            SpokenText.parkingTakeMe(distance, direction)
        } else {
            SpokenText.parkingHere(distance, direction)
        }
    }

    private fun distanceWords(a: Pair<Double, Double>, b: Pair<Double, Double>): String {
        val metres = haversineMetres(a, b)
        return if (metres < 950) {
            "${((metres / 10).roundToInt() * 10).coerceAtLeast(10)} metros"
        } else {
            "%.1f km".format(SPANISH_ES, metres / 1000.0)
        }
    }

    private fun haversineMetres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val la1 = Math.toRadians(a.first)
        val la2 = Math.toRadians(b.first)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    /** 0=norte, 1=noreste … 7=noroeste — index into [COMPASS_ES]. */
    private fun bearingIndex(a: Pair<Double, Double>, b: Pair<Double, Double>): Int {
        val dLon = Math.toRadians(b.second - a.second)
        val la1 = Math.toRadians(a.first)
        val la2 = Math.toRadians(b.first)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        val deg = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return (((deg + 22.5) / 45.0).toInt()) % 8
    }

    /** A `"lat,lon"` fact value → coordinate pair, or null if it doesn't parse. */
    private fun String.toCoords(): Pair<Double, Double>? {
        val parts = split(",").map { it.trim().toDoubleOrNull() }
        return if (parts.size == 2 && parts[0] != null && parts[1] != null) {
            parts[0]!! to parts[1]!!
        } else {
            null
        }
    }

    private fun handleMediaRequest(app: MediaApp): String =
        if (mediaRepository.launchMediaApp(app)) {
            SpokenText.openingApp(app.displayName)
        } else {
            SpokenText.cantOpenApp(app.displayName)
        }

    /** "pon música" — no app named, so open whichever one the driver has. */
    private fun handleAnyMusicRequest(): String =
        mediaRepository.launchAnyMusicApp()
            ?.let { SpokenText.openingApp(it.displayName) }
            ?: SpokenText.NO_MUSIC_APP
}
