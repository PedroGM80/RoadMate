package dev.pgm.roadmate.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.model.UserLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Every voice parser, exercised on the real device.
 *
 * The unit tests for these run on the JVM's `java.util.regex`; the app runs
 * on Android's ICU engine, and the two don't accept the same syntax. A
 * pattern only ICU rejects used to surface as an `ExceptionInInitializerError`
 * that killed the app at launch (the `(?U)` flag). `spanishRegex` now degrades
 * instead of throwing — which means a broken pattern goes *silent* rather than
 * loud. This test is the thing that stays loud: it asserts each parser still
 * recognises a canonical utterance once its class has initialised on ICU.
 */
@RunWith(AndroidJUnit4::class)
class ParsersOnDeviceTest {

    private val ctx = TravelContext(
        currentLocation = UserLocation(36.46, -6.19), hour = 12, minute = 0, date = Date(), userInput = "",
    )

    @Test
    fun weatherIntent() {
        assertTrue(WeatherIntentParser.isWeatherQuestion("¿qué tiempo hace?"))
        assertTrue(WeatherIntentParser.isWeatherQuestion("el tiempo meteorológico en Cádiz"))
        assertNull(WeatherIntentParser.placeIn("¿qué tiempo hace?"))
        assertEquals("Cádiz", WeatherIntentParser.placeIn("dime el tiempo de Cádiz"))
    }

    @Test
    fun mapSearchIntent() {
        assertTrue(MapSearchIntentParser.isNavigationRequest("llévame a Chiclana"))
        assertNotNull(MapSearchIntentParser.extractSearchQuery("busca una gasolinera cerca"))
    }

    @Test
    fun callIntent() {
        assertTrue(
            CallIntentParser.extractContactName("llama a Ana García")
                ?.contains("Ana", ignoreCase = true) == true,
        )
    }

    @Test
    fun mediaIntent() {
        assertNotNull(MediaIntentParser.extractMediaApp("abre Spotify"))
        assertTrue(MediaIntentParser.wantsMusicWithoutApp("pon música"))
    }

    @Test
    fun placeCategory() {
        assertNotNull(PlaceCategoryParser.parse("una gasolinera"))
        assertNull(PlaceCategoryParser.parse("el faro de Chipiona"))
    }

    @Test
    fun memoryCommand() {
        assertEquals(MemoryCommandParser.Command.SetHome, MemoryCommandParser.parse("esta es mi casa"))
        // Named-capture-group regex ((?<n1>…)/(?<r1>…)) — an ICU-syntax risk.
        assertEquals(
            MemoryCommandParser.Command.SetRelationship("jefa", "Marta García"),
            MemoryCommandParser.parse("guarda a Marta García como mi jefa"),
        )
        assertEquals(
            MemoryCommandParser.Command.SetRelationship("hermano", "Juan"),
            MemoryCommandParser.parse("Juan es mi hermano"),
        )
        assertNotNull(MemoryCommandParser.parse("recuerda que no me gustan las autovías"))
    }

    @Test
    fun stylePreference() {
        assertNotNull(StylePreferenceParser.parse("dame respuestas más breves"))
        assertNull(StylePreferenceParser.parse("busca una gasolinera"))
    }

    @Test
    fun arithmetic() {
        assertEquals("12 entre 4 son 3.", ArithmeticParser.evaluate("cuánto es doce entre cuatro"))
        assertNull(ArithmeticParser.evaluate("cuéntame un chiste"))
    }

    @Test
    fun callFollowUp() {
        // Its class only needs to initialise on ICU without falling back; a
        // no-candidates call returns null and that's fine.
        assertNull(CallFollowUpParser.resolve("la primera", emptyList()))
    }

    @Test
    fun questionPunctuation() {
        assertEquals("¿qué hora es?", QuestionPunctuation.normalize("qué hora es"))
        assertEquals("pon música", QuestionPunctuation.normalize("pon música"))
    }

    @Test
    fun placeName() {
        assertEquals("hotel nh cádiz", PlaceName.normalize("  El Hotel NH Cádiz "))
    }

    @Test
    fun contactMatching() {
        assertEquals("ana garcia", ContactMatching.fold("Ána García"))
    }

    @Test
    fun promptBuilderRunsItsRegexes() {
        val prompt = PromptBuilder.buildPrompt(ctx.copy(userInput = "¿dónde estoy?"), "¿dónde estoy?")
        assertTrue(prompt.isNotBlank())
    }
}
