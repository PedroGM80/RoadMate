package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.TravelContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PromptBuilderTest {

    @Test
    fun `applies the requested answer style, defaulting to normal length`() {
        val context = TravelContext(null, null, 9, Date(), "hola")

        val normal = PromptBuilder.buildPrompt(context, "hola")
        val brief = PromptBuilder.buildPrompt(context, "hola", AnswerStyle.BRIEF)

        assertTrue(normal.contains(AnswerStyle.NORMAL.promptInstruction))
        assertTrue(brief.contains(AnswerStyle.BRIEF.promptInstruction))
        assertFalse(brief.contains(AnswerStyle.NORMAL.promptInstruction))
    }

    @Test
    fun `includes coordinates when location is known`() {
        val context = TravelContext(
            currentLocation = 36.4614 to -6.1998,
            hour = 14,
            date = Date(),
            userInput = "¿cuánto queda?"
        )

        val prompt = PromptBuilder.buildPrompt(context, context.userInput)

        assertTrue(prompt.contains("36.4614"))
        assertTrue(prompt.contains("-6.1998"))
    }

    @Test
    fun `falls back to unknown location text when null`() {
        val context = TravelContext(
            currentLocation = null,
            hour = 9,
            date = Date(),
            userInput = "hola"
        )

        val prompt = PromptBuilder.buildPrompt(context, context.userInput)

        assertTrue(prompt.contains("desconocida"))
    }

    @Test
    fun `includes destination only when present`() {
        val withDestination = PromptBuilder.buildPrompt(
            TravelContext(null, "San Fernando", 10, Date(), "hola"),
            "hola"
        )
        val withoutDestination = PromptBuilder.buildPrompt(
            TravelContext(null, null, 10, Date(), "hola"),
            "hola"
        )

        assertTrue(withDestination.contains("San Fernando"))
        assertTrue(withoutDestination.contains("sin destino definido"))
    }

    @Test
    fun `truncates to MAX_CONTEXT_LENGTH`() {
        val hugeInput = "a".repeat(Constants.MAX_CONTEXT_LENGTH * 2)
        val context = TravelContext(null, null, 0, Date(), hugeInput)

        val prompt = PromptBuilder.buildPrompt(context, hugeInput)

        assertTrue(prompt.length <= Constants.MAX_CONTEXT_LENGTH)
    }

    @Test
    fun `omits the conversation section when there are no recent exchanges`() {
        val context = TravelContext(null, null, 0, Date(), "hola")

        val prompt = PromptBuilder.buildPrompt(context, "hola")

        assertFalse(prompt.contains("Antes en esta conversación"))
    }

    @Test
    fun `includes only the last three exchanges`() {
        val context = TravelContext(null, null, 0, Date(), "hola")
        val exchanges = listOf("uno", "dos", "tres", "cuatro").map { Exchange("p-$it", "r-$it") }

        val prompt = PromptBuilder.buildPrompt(context, "hola", recentExchanges = exchanges)

        assertFalse(prompt.contains("p-uno"))
        assertTrue(prompt.contains("p-dos"))
        assertTrue(prompt.contains("r-tres"))
        assertTrue(prompt.contains("p-cuatro"))
    }
}
