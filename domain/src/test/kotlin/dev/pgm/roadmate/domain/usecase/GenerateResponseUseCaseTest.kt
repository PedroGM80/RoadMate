package dev.pgm.roadmate.domain.usecase

import dev.pgm.roadmate.domain.fake.FakeAssistantPreferencesRepository
import dev.pgm.roadmate.domain.fake.FakeGeminiRepository
import dev.pgm.roadmate.domain.fake.FakeMapSearchCoordinator
import dev.pgm.roadmate.domain.fake.FakeMediaRepository
import dev.pgm.roadmate.domain.fake.FakeMemoryRepository
import dev.pgm.roadmate.domain.fake.FakeMessagingRepository
import dev.pgm.roadmate.domain.fake.FakePhoneCallRepository
import dev.pgm.roadmate.domain.fake.FakeSpeechSynthesisRepository
import dev.pgm.roadmate.domain.fake.FakeWeatherRepository
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.MediaApp
import dev.pgm.roadmate.domain.model.PlaceCategory
import dev.pgm.roadmate.domain.model.PhoneLabel
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.utils.JokeProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GenerateResponseUseCaseTest {

    private val context = TravelContext(currentLocation = 36.46 to -6.19, hour = 12, date = Date(), userInput = "")

    private fun useCase(
        geminiRepository: FakeGeminiRepository = FakeGeminiRepository(),
        speechSynthesisRepository: FakeSpeechSynthesisRepository = FakeSpeechSynthesisRepository(),
        phoneCallRepository: FakePhoneCallRepository = FakePhoneCallRepository(),
        mapSearchCoordinator: FakeMapSearchCoordinator = FakeMapSearchCoordinator(),
        mediaRepository: FakeMediaRepository = FakeMediaRepository(),
        assistantPreferencesRepository: FakeAssistantPreferencesRepository = FakeAssistantPreferencesRepository(),
        memoryRepository: FakeMemoryRepository = FakeMemoryRepository(),
        weatherRepository: FakeWeatherRepository = FakeWeatherRepository(),
        messagingRepository: FakeMessagingRepository = FakeMessagingRepository()
    ) = GenerateResponseUseCase(
        geminiRepository,
        speechSynthesisRepository,
        phoneCallRepository,
        mapSearchCoordinator,
        mediaRepository,
        assistantPreferencesRepository,
        memoryRepository,
        weatherRepository,
        messagingRepository
    )

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
    fun `weather questions answer from context, not Gemini`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")
        val speech = FakeSpeechSynthesisRepository()
        val withWeather = context.copy(weatherDescription = "cielo despejado, 18°C")

        val emitted = useCase(gemini, speech)(withWeather, "¿qué tiempo hace?").toList()

        assertEquals(0, gemini.responseCount)
        assertEquals(listOf("Ahora mismo: cielo despejado, 18°C."), emitted)
        assertEquals("Ahora mismo: cielo despejado, 18°C.", speech.lastSpoken)
    }

    @Test
    fun `weather for a named place is fetched for that place, not here`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")
        val weather = FakeWeatherRepository(
            here = "cielo despejado, 18°C",
            byName = mapOf("Ronda" to "lluvia ligera, 11°C"),
        )
        val withHere = context.copy(weatherDescription = "cielo despejado, 18°C")

        val emitted = useCase(gemini, weatherRepository = weather)(withHere, "¿qué tiempo hace en Ronda?").toList()

        assertEquals(listOf("En Ronda: lluvia ligera, 11°C."), emitted)
        assertEquals(listOf("Ronda"), weather.requestedNames)
    }

    @Test
    fun `weather for an unresolvable place says so plainly`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")
        val weather = FakeWeatherRepository(here = "cielo despejado, 18°C")

        val emitted = useCase(gemini, weatherRepository = weather)(context, "¿qué tiempo hace en Narnia?").toList()

        assertEquals(listOf("No consigo el tiempo de Narnia ahora mismo."), emitted)
    }

    @Test
    fun `"dile a X que Y" sends an SMS to the resolved contact`() = runTest {
        val phone = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Found(ContactMatch("Ana García", "600111222")),
        )
        val sms = FakeMessagingRepository()

        val emitted = useCase(phoneCallRepository = phone, messagingRepository = sms)(
            context, "dile a Ana que llego en veinte minutos",
        ).toList()

        assertEquals(listOf("Mensaje enviado a Ana García."), emitted)
        assertEquals("600111222", sms.sentTo)
        assertEquals("llego en veinte minutos", sms.sentBody)
    }

    @Test
    fun `a message to an unknown contact is not sent`() = runTest {
        val sms = FakeMessagingRepository()
        val emitted = useCase(messagingRepository = sms)(context, "manda un mensaje a Nadie: hola").toList()

        assertEquals(listOf("No encuentro a Nadie en tus contactos."), emitted)
        assertNull(sms.sentTo)
    }

    @Test
    fun `"dónde estoy" is answered from the map label, not the model`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")
        val ctx = context.copy(placeLabel = "Calle Real · San Fernando")

        val emitted = useCase(gemini)(ctx, "¿dónde estoy?").toList()

        assertEquals(listOf("Estás en Calle Real, San Fernando."), emitted)
        assertEquals(0, gemini.responseCount)
    }

    @Test
    fun `"dónde estoy" with no map label falls through to the model`() = runTest {
        val gemini = FakeGeminiRepository(response = "estás cerca de Cádiz")

        val emitted = useCase(gemini)(context, "¿dónde estoy?").toList()

        assertEquals(listOf("estás cerca de Cádiz"), emitted)
    }

    @Test
    fun `parking is saved, then found by distance and bearing`() = runTest {
        val mem = FakeMemoryRepository()
        val uc = useCase(memoryRepository = mem)

        val parked = context.copy(currentLocation = 36.4609 to -6.19) // ~100 m north of "here"
        assertEquals(listOf("Vale, guardo dónde has aparcado."), uc(parked, "he aparcado aquí").toList())

        val found = uc(context, "¿dónde aparqué?").toList()
        assertEquals(listOf("El coche está a unos 100 metros, hacia el norte."), found)
    }

    @Test
    fun `asking for the car with nothing saved says so`() = runTest {
        val emitted = useCase()(context, "¿dónde está el coche?").toList()
        assertEquals(listOf("No tengo guardado dónde aparcaste."), emitted)
    }

    @Test
    fun `"repite" says the last answer again`() = runTest {
        val speech = FakeSpeechSynthesisRepository()
        val uc = useCase(FakeGeminiRepository(response = "quedan 12 kilómetros"), speech)

        uc(context, "¿cuánto queda?").toList()
        val repeated = uc(context, "repite").toList()

        assertEquals(listOf("quedan 12 kilómetros"), repeated)
        assertEquals("quedan 12 kilómetros", speech.lastSpoken)
    }

    @Test
    fun `"repite" before anything was said explains that`() = runTest {
        val emitted = useCase()(context, "repite eso").toList()
        assertEquals(listOf("Todavía no he dicho nada."), emitted)
    }

    @Test
    fun `"más despacio" lowers and persists the speech rate`() = runTest {
        val prefs = FakeAssistantPreferencesRepository()
        val emitted = useCase(assistantPreferencesRepository = prefs)(context, "habla más despacio").toList()

        assertEquals(listOf("Vale, hablo más despacio."), emitted)
        assertEquals(0.85f, prefs.speechRateFlow.value, 0.001f)
    }

    @Test
    fun `weather question with no data says so plainly instead of guessing`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")

        val emitted = useCase(gemini)(context, "¿va a llover?").toList()

        assertEquals(0, gemini.responseCount)
        assertEquals(listOf("No puedo consultar el tiempo ahora mismo."), emitted)
    }

    @Test
    fun `"cuánto tiempo queda" is not treated as a weather question`() = runTest {
        val gemini = FakeGeminiRepository(response = "quedan 20 minutos")

        val emitted = useCase(gemini)(context, "¿cuánto tiempo queda?").toList()

        assertEquals(1, gemini.responseCount)
        assertEquals(listOf("quedan 20 minutos"), emitted)
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
    fun `one contact with two numbers asks which number, then a label finishes it`() = runTest {
        val phone = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Ambiguous(
                listOf(
                    ContactMatch("Ana", "600111222", PhoneLabel.MOBILE),
                    ContactMatch("Ana", "955000000", PhoneLabel.WORK),
                ),
            ),
        )
        val uc = useCase(phoneCallRepository = phone)

        val ask = uc(context, "llama a Ana").toList()
        assertEquals(null, phone.placedCallTo)
        assertTrue(ask.first().contains("varios números"))

        val emitted = uc(context, "la del trabajo").toList()
        assertEquals("955000000", phone.placedCallTo)
        assertEquals(listOf("Llamando a Ana"), emitted)
    }

    @Test
    fun `a follow-up after an ambiguous call finishes it`() = runTest {
        val phone = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Ambiguous(
                listOf(ContactMatch("Ana García", "600111222"), ContactMatch("Ana López", "600333444")),
            ),
        )
        val uc = useCase(phoneCallRepository = phone)

        uc(context, "llama a Ana").toList()
        val emitted = uc(context, "la segunda").toList()

        assertEquals("600333444", phone.placedCallTo)
        assertEquals(listOf("Llamando a Ana López"), emitted)
    }

    @Test
    fun `an unrelated utterance after an ambiguous call is handled normally`() = runTest {
        val phone = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Ambiguous(
                listOf(ContactMatch("Ana García", "1"), ContactMatch("Ana López", "2")),
            ),
        )
        val uc = useCase(phoneCallRepository = phone)

        uc(context, "llama a Ana").toList()
        val emitted = uc(context, "cuéntame un chiste").toList()

        assertEquals(null, phone.placedCallTo)
        assertTrue(JokeProvider.matchesJokeIntent("cuéntame un chiste"))
        assertEquals(1, emitted.size)
    }

    @Test
    fun `call requests without permission explain instead of failing silently`() = runTest {
        val phoneCallRepository = FakePhoneCallRepository(hasPermission = false)

        val emitted = useCase(phoneCallRepository = phoneCallRepository)(context, "llama a Ana").toList()

        assertEquals(null, phoneCallRepository.placedCallTo)
        assertTrue(emitted.first().contains("permiso"))
    }

    @Test
    fun `a category map search is submitted to the offline map and bypasses Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val coordinator = FakeMapSearchCoordinator()

        val emitted = useCase(geminiRepository, mapSearchCoordinator = coordinator)(
            context,
            "busca una gasolinera cerca"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals("una gasolinera", coordinator.lastRequest?.rawQuery)
        assertEquals(PlaceCategory.FUEL, coordinator.lastRequest?.category)
        assertEquals(36.46 to -6.19, coordinator.lastRequest?.origin)
        assertTrue(emitted.first().contains("gasolinera"))
    }

    @Test
    fun `dónde hay map search requests are also recognized as a category`() = runTest {
        val coordinator = FakeMapSearchCoordinator()

        useCase(mapSearchCoordinator = coordinator)(context, "dónde hay un hotel").toList()

        assertEquals("un hotel", coordinator.lastRequest?.rawQuery)
        assertEquals(PlaceCategory.HOTEL, coordinator.lastRequest?.category)
    }

    @Test
    fun `a take-me-there request submits with navigate = true and says so`() = runTest {
        val coordinator = FakeMapSearchCoordinator()

        val emitted = useCase(mapSearchCoordinator = coordinator)(context, "llévame a la playa").toList()

        assertEquals("la playa", coordinator.lastRequest?.rawQuery)
        assertEquals(true, coordinator.lastRequest?.navigate)
        assertTrue(emitted.first().contains("Te llevo"))
    }

    @Test
    fun `take me home routes straight to the saved HOME coordinate`() = runTest {
        val coordinator = FakeMapSearchCoordinator()
        val memory = FakeMemoryRepository(
            initialFacts = listOf(UserFact(FactType.HOME, value = "40.4168,-3.7038")),
        )

        val emitted = useCase(mapSearchCoordinator = coordinator, memoryRepository = memory)(
            context, "llévame a casa",
        ).toList()

        assertEquals(40.4168 to -3.7038, coordinator.lastRequest?.destination)
        assertEquals(true, coordinator.lastRequest?.navigate)
        assertTrue(emitted.first().contains("Te llevo a casa"))
    }

    @Test
    fun `take me to work with nothing saved asks the driver to set it first`() = runTest {
        val coordinator = FakeMapSearchCoordinator()

        val emitted = useCase(mapSearchCoordinator = coordinator)(context, "llévame al trabajo").toList()

        assertTrue(coordinator.submitted.isEmpty())
        assertTrue(emitted.first().contains("No sé dónde está tu trabajo"))
    }

    @Test
    fun `a named place with no category is still submitted, category null`() = runTest {
        val coordinator = FakeMapSearchCoordinator()

        useCase(mapSearchCoordinator = coordinator)(context, "busca el Mercadona").toList()

        assertEquals("el Mercadona", coordinator.lastRequest?.rawQuery)
        assertEquals(null, coordinator.lastRequest?.category)
    }

    @Test
    fun `a map search with no downloaded region says so and submits nothing`() = runTest {
        val coordinator = FakeMapSearchCoordinator(offlineMapReady = false)

        val emitted = useCase(mapSearchCoordinator = coordinator)(context, "busca una gasolinera")
            .toList()

        assertTrue(coordinator.submitted.isEmpty())
        assertTrue(emitted.first().contains("descargado"))
    }

    @Test
    fun `media requests launch the app and bypass Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val mediaRepository = FakeMediaRepository()

        val emitted = useCase(geminiRepository, mediaRepository = mediaRepository)(
            context,
            "pon música en Spotify"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(MediaApp.SPOTIFY, mediaRepository.lastLaunchedApp)
        assertEquals(listOf("Abro Spotify."), emitted)
    }

    @Test
    fun `a media request for an app that is not installed explains instead of claiming success`() = runTest {
        val mediaRepository = FakeMediaRepository(canLaunch = false)

        val emitted = useCase(mediaRepository = mediaRepository)(context, "abre YouTube Music").toList()

        assertEquals(MediaApp.YOUTUBE_MUSIC, mediaRepository.lastLaunchedApp)
        assertTrue(emitted.first().contains("No puedo abrir"))
    }

    @Test
    fun `an answer-style command persists the preference, acks it, and skips Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val prefs = FakeAssistantPreferencesRepository()

        val emitted = useCase(geminiRepository, assistantPreferencesRepository = prefs)(
            context,
            "de ahora en adelante respuestas cortas"
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(AnswerStyle.BRIEF, prefs.answerStyle.value)
        assertEquals(listOf(AnswerStyle.BRIEF.spokenAck), emitted)
    }

    @Test
    fun `the stored answer style is folded into the Gemini prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val prefs = FakeAssistantPreferencesRepository(initial = AnswerStyle.BRIEF)

        useCase(geminiRepository, assistantPreferencesRepository = prefs)(context, "¿cuánto queda?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains(AnswerStyle.BRIEF.promptInstruction))
    }

    @Test
    fun `a real question and its answer are written to memory`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "unos 40 minutos")
        val memory = FakeMemoryRepository()

        useCase(geminiRepository, memoryRepository = memory)(context, "¿cuánto queda a Cádiz?").toList()

        assertEquals(1, memory.recorded.size)
        assertEquals(Exchange("¿cuánto queda a Cádiz?", "unos 40 minutos"), memory.recorded.first())
    }

    @Test
    fun `recent exchanges from memory are folded into the prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val memory = FakeMemoryRepository(initial = listOf(Exchange("¿distancia a Cádiz?", "32 km")))

        useCase(geminiRepository, memoryRepository = memory)(context, "¿y en coche?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains("¿distancia a Cádiz?"))
        assertTrue(geminiRepository.lastPrompt!!.contains("32 km"))
    }

    @Test
    fun `shortcut answers are not written to memory`() = runTest {
        val memory = FakeMemoryRepository()

        useCase(memoryRepository = memory)(context, "cuéntame un chiste").toList()
        useCase(memoryRepository = memory)(context, "abre Spotify").toList()

        assertTrue(memory.recorded.isEmpty())
    }

    @Test
    fun `recuerda que stores a preference and skips Gemini`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "no debería usarse")
        val memory = FakeMemoryRepository()

        val emitted = useCase(geminiRepository, memoryRepository = memory)(
            context,
            "recuerda que no me gustan las autovías",
        ).toList()

        assertEquals(0, geminiRepository.responseCount)
        assertEquals(
            listOf(UserFact(FactType.PREFERENCE, value = "no me gustan las autovías")),
            memory.facts(FactType.PREFERENCE),
        )
        assertEquals(listOf("Anotado."), emitted)
    }

    @Test
    fun `stored preferences are folded into the Gemini prompt`() = runTest {
        val geminiRepository = FakeGeminiRepository(response = "vale")
        val memory = FakeMemoryRepository(
            initialFacts = listOf(UserFact(FactType.PREFERENCE, value = "no me gustan las autovías")),
        )

        useCase(geminiRepository, memoryRepository = memory)(context, "¿por dónde voy?").toList()

        assertTrue(geminiRepository.lastPrompt!!.contains("no me gustan las autovías"))
    }

    @Test
    fun `que sabes de mi reads the stored preferences back`() = runTest {
        val memory = FakeMemoryRepository(
            initialFacts = listOf(UserFact(FactType.PREFERENCE, value = "prefiere las nacionales")),
        )

        val emitted = useCase(memoryRepository = memory)(context, "¿qué sabes de mí?").toList()

        assertTrue(emitted.first().contains("prefiere las nacionales"))
    }

    @Test
    fun `a map search records the place and later feeds it into the prompt`() = runTest {
        val memory = FakeMemoryRepository()
        val gemini = FakeGeminiRepository(response = "vale")

        useCase(gemini, memoryRepository = memory)(context, "busca gasolineras").toList()
        useCase(gemini, memoryRepository = memory)(context, "¿por dónde sigo?").toList()

        assertEquals(listOf("gasolineras"), memory.frequentPlaces().map { it.value })
        assertTrue(gemini.lastPrompt!!.contains("gasolineras"))
    }

    @Test
    fun `esta es mi casa saves the current location as HOME`() = runTest {
        val memory = FakeMemoryRepository()

        val emitted = useCase(memoryRepository = memory)(context, "esta es mi casa").toList()

        assertEquals(listOf(UserFact(FactType.HOME, value = "36.46,-6.19")), memory.facts(FactType.HOME))
        assertTrue(emitted.first().contains("casa"))
    }

    @Test
    fun `set home with no location explains instead of saving`() = runTest {
        val memory = FakeMemoryRepository()
        val noLocation = context.copy(currentLocation = null)

        val emitted = useCase(memoryRepository = memory)(noLocation, "esta es mi casa").toList()

        assertTrue(memory.facts(FactType.HOME).isEmpty())
        assertTrue(emitted.first().contains("ubicación"))
    }

    @Test
    fun `a relationship is stored and then resolves a call`() = runTest {
        val memory = FakeMemoryRepository()
        val phone = FakePhoneCallRepository(
            lookupResult = ContactLookupResult.Found(ContactMatch("Juan", "600999888")),
        )

        useCase(memoryRepository = memory)(context, "Juan es mi hermano").toList()
        val emitted = useCase(phoneCallRepository = phone, memoryRepository = memory)(
            context, "llama a mi hermano",
        ).toList()

        assertEquals("600999888", phone.placedCallTo)
        assertEquals(listOf("Llamando a Juan"), emitted)
    }

    @Test
    fun `calling an unknown relationship asks to be taught first`() = runTest {
        val phone = FakePhoneCallRepository()

        val emitted = useCase(phoneCallRepository = phone, memoryRepository = FakeMemoryRepository())(
            context, "llama a mi hermano",
        ).toList()

        assertEquals(null, phone.placedCallTo)
        assertTrue(emitted.first().contains("No sé quién es tu hermano"))
    }

    @Test
    fun `que te dije sobre X searches past conversation, bypassing Gemini`() = runTest {
        val gemini = FakeGeminiRepository(response = "no debería usarse")
        val memory = FakeMemoryRepository(
            initial = listOf(Exchange("¿hay hotel en Ronda?", "sí, el Parador está bien")),
        )

        val emitted = useCase(gemini, memoryRepository = memory)(
            context, "¿qué te dije sobre el hotel de Ronda?",
        ).toList()

        assertEquals(0, gemini.responseCount)
        assertTrue(emitted.first().contains("el Parador está bien"))
    }

    @Test
    fun `a search with nothing on record says so`() = runTest {
        val emitted = useCase(memoryRepository = FakeMemoryRepository())(
            context, "¿qué te dije sobre el hotel de Ronda?",
        ).toList()

        assertTrue(emitted.first().contains("No encuentro"))
    }
}
