package dev.pgm.roadmate.utils

import dev.pgm.roadmate.utils.PlaybackCommandParser.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCommandParserTest {

    @Test
    fun `recognises repeat phrasings`() {
        listOf(
            "repite",
            "repítelo",
            "¿qué has dicho?",
            "otra vez",
            "no te he oído",
            "vuelve a decirlo",
        ).forEach { assertEquals(it, Command.REPEAT, PlaybackCommandParser.parse(it)) }
    }

    @Test
    fun `recognises speed phrasings`() {
        assertEquals(Command.SLOWER, PlaybackCommandParser.parse("habla más despacio"))
        assertEquals(Command.SLOWER, PlaybackCommandParser.parse("vas muy rápido"))
        assertEquals(Command.FASTER, PlaybackCommandParser.parse("más rápido"))
        assertEquals(Command.NORMAL_SPEED, PlaybackCommandParser.parse("voz normal"))
    }

    @Test
    fun `leaves real questions alone`() {
        listOf(
            "¿cuánto queda?",
            "pon música",
            "llama a Ana",
            "¿qué tiempo hace?",
            "repítele a García que llego",
        ).forEach { assertNull(it, PlaybackCommandParser.parse(it)) }
    }
}
