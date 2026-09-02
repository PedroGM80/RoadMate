package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageIntentParserTest {

    @Test
    fun `pulls recipient and body`() {
        mapOf(
            "manda un mensaje a Ana: llego en 20" to ("Ana" to "llego en 20"),
            "dile a Juan que voy de camino" to ("Juan" to "voy de camino"),
            "escríbele a mamá diciendo que llego tarde" to ("Mamá" to "llego tarde"),
            "avísale a Pedro que salgo ya" to ("Pedro" to "salgo ya"),
        ).forEach { (input, expected) ->
            val r = MessageIntentParser.parse(input)
            assertEquals(input, expected.first, r?.recipient)
            assertEquals(input, expected.second, r?.body)
        }
    }

    @Test
    fun `not a message`() {
        listOf(
            "llama a Ana",
            "¿cuánto queda?",
            "pon música",
            "llévame a casa",
        ).forEach { assertNull(it, MessageIntentParser.parse(it)) }
    }
}
