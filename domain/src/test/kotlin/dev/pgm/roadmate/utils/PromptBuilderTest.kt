package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.TravelContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PromptBuilderTest {

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
    fun `omits last responses section when there are none`() {
        val context = TravelContext(null, null, 0, Date(), "hola", lastResponses = emptyList())

        val prompt = PromptBuilder.buildPrompt(context, "hola")

        assertFalse(prompt.contains("Respuestas anteriores"))
    }

    @Test
    fun `includes only the last three responses`() {
        val context = TravelContext(
            currentLocation = null,
            hour = 0,
            date = Date(),
            userInput = "hola",
            lastResponses = listOf("uno", "dos", "tres", "cuatro")
        )

        val prompt = PromptBuilder.buildPrompt(context, "hola")

        assertFalse(prompt.contains("- uno"))
        assertTrue(prompt.contains("- dos"))
        assertTrue(prompt.contains("- tres"))
        assertTrue(prompt.contains("- cuatro"))
    }
}
