package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.model.UserLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PromptBuilderTest {

    private fun context(
        location: UserLocation? = null,
        destination: String? = null,
        hour: Int = 9,
        minute: Int = 0,
        input: String = "hola",
    ) = TravelContext(
        currentLocation = location,
        destination = destination,
        hour = hour,
        date = Date(),
        userInput = input,
        minute = minute,
    )

    @Test
    fun `applies the requested answer style, defaulting to normal length`() {
        val normal = PromptBuilder.buildPrompt(context(), "hola")
        val brief = PromptBuilder.buildPrompt(context(), "hola", AnswerStyle.BRIEF)

        assertTrue(normal.contains(AnswerStyle.NORMAL.promptInstruction))
        assertTrue(brief.contains(AnswerStyle.BRIEF.promptInstruction))
        assertFalse(brief.contains(AnswerStyle.NORMAL.promptInstruction))
    }

    @Test
    fun `ends with the question then an answer cue so the model answers, not rephrases`() {
        val prompt = PromptBuilder.buildPrompt(context(input = "¿cuánto queda?"), "¿cuánto queda?")

        assertTrue(prompt.contains("Pregunta del conductor: ¿cuánto queda?"))
        assertTrue(prompt.trimEnd().endsWith("Respuesta:"))
    }

    @Test
    fun `renders the real time, not a rounded hour`() {
        val prompt = PromptBuilder.buildPrompt(context(hour = 14, minute = 37), "hola")

        assertTrue(prompt.contains("Hora: 14:37"))
    }

    @Test
    fun `includes coordinates when location is known`() {
        val prompt = PromptBuilder.buildPrompt(
            context(location = UserLocation(36.4614, -6.1998), input = "¿cuánto queda?"),
            "¿cuánto queda?",
        )

        assertTrue(prompt.contains("36.4614"))
        assertTrue(prompt.contains("-6.1998"))
    }

    @Test
    fun `falls back to unknown location text when null`() {
        val prompt = PromptBuilder.buildPrompt(
            context(input = "¿cuánto queda?"),
            "¿cuánto queda?",
        )

        assertTrue(prompt.contains("desconocida"))
    }

    @Test
    fun `omits coordinates entirely when the question is not spatial`() {
        val prompt = PromptBuilder.buildPrompt(
            context(location = UserLocation(36.4614, -6.1998), input = "¿quién pintó el Guernica?"),
            "¿quién pintó el Guernica?",
        )

        assertFalse(prompt.contains("Ubicación"))
        assertFalse(prompt.contains("36.4614"))
    }

    @Test
    fun `includes coordinates when the question is spatial`() {
        val prompt = PromptBuilder.buildPrompt(
            context(location = UserLocation(36.4614, -6.1998), input = "¿estamos cerca?"),
            "¿estamos cerca?",
        )

        assertTrue(prompt.contains("Ubicación (lat,lon): 36.4614, -6.1998"))
    }

    @Test
    fun `omits the weather line unless the question is weather-related`() {
        val plain = context(input = "¿qué hora es?").copy(weatherDescription = "cielo despejado")
        val rainy = context(input = "¿me llevo paraguas?").copy(weatherDescription = "lluvia ligera")

        assertFalse(PromptBuilder.buildPrompt(plain, "¿qué hora es?").contains("Clima:"))
        assertTrue(
            PromptBuilder.buildPrompt(rainy, "¿me llevo paraguas?").contains("Clima: lluvia ligera"),
        )
    }

    @Test
    fun `omits home and work coordinates unless the question is spatial`() {
        val chat = PromptBuilder.buildPrompt(
            context(input = "cuéntame un chiste"),
            "cuéntame un chiste",
            home = "40.0,-3.7",
            work = "40.1,-3.6",
        )
        val route = PromptBuilder.buildPrompt(
            context(input = "¿cuánto falta para casa?"),
            "¿cuánto falta para casa?",
            home = "40.0,-3.7",
            work = "40.1,-3.6",
        )

        assertFalse(chat.contains("Casa (lat,lon)"))
        assertFalse(chat.contains("Trabajo (lat,lon)"))
        assertTrue(route.contains("Casa (lat,lon): 40.0,-3.7"))
        assertTrue(route.contains("Trabajo (lat,lon): 40.1,-3.6"))
    }

    @Test
    fun `includes destination only when present`() {
        val with = PromptBuilder.buildPrompt(context(destination = "San Fernando"), "hola")
        val without = PromptBuilder.buildPrompt(context(destination = null), "hola")

        assertTrue(with.contains("Destino: San Fernando"))
        assertFalse(without.contains("Destino:"))
    }

    @Test
    fun `truncates to MAX_CONTEXT_LENGTH`() {
        val hugeInput = "a".repeat(Constants.MAX_CONTEXT_LENGTH * 2)

        val prompt = PromptBuilder.buildPrompt(context(input = hugeInput), hugeInput)

        assertTrue(prompt.length <= Constants.MAX_CONTEXT_LENGTH)
    }

    @Test
    fun `flattens newlines in interpolated values to keep one field per line`() {
        val prompt = PromptBuilder.buildPrompt(
            context(input = "línea uno\nlínea dos\nlínea tres"),
            "línea uno\nlínea dos\nlínea tres",
        )

        assertTrue(prompt.contains("Pregunta del conductor: línea uno línea dos línea tres"))
    }

    @Test
    fun `omits the previous-turn section when there are no recent exchanges`() {
        assertFalse(PromptBuilder.buildPrompt(context(), "hola").contains("Turno anterior"))
    }

    @Test
    fun `includes only the single most recent exchange`() {
        val exchanges = listOf("uno", "dos", "tres").map { Exchange("p-$it", "r-$it") }

        val prompt = PromptBuilder.buildPrompt(context(), "hola", recentExchanges = exchanges)

        assertTrue(prompt.contains("Turno anterior"))
        assertFalse(prompt.contains("p-uno"))
        assertFalse(prompt.contains("p-dos"))
        assertTrue(prompt.contains("p-tres"))
        assertTrue(prompt.contains("r-tres"))
    }

    @Test
    fun `caps the length of the rendered exchange line`() {
        val long = "x".repeat(400)

        val prompt = PromptBuilder.buildPrompt(
            context(), "hola",
            recentExchanges = listOf(Exchange(long, long)),
        )

        assertFalse(prompt.contains("x".repeat(200)))
    }
}
