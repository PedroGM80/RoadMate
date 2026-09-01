package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.TravelContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PromptBuilderTest {

    private fun context(
        location: Pair<Double, Double>? = null,
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
            context(location = 36.4614 to -6.1998, input = "¿cuánto queda?"),
            "¿cuánto queda?",
        )

        assertTrue(prompt.contains("36.4614"))
        assertTrue(prompt.contains("-6.1998"))
    }

    @Test
    fun `falls back to unknown location text when null`() {
        assertTrue(PromptBuilder.buildPrompt(context(), "hola").contains("desconocida"))
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
