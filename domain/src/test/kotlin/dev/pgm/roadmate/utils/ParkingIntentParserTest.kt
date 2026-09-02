package dev.pgm.roadmate.utils

import dev.pgm.roadmate.utils.ParkingIntentParser.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkingIntentParserTest {

    @Test
    fun `save phrasings`() {
        listOf(
            "he aparcado aquí",
            "aparco aquí",
            "guarda dónde he aparcado",
            "recuerda dónde dejé el coche",
            "marca el aparcamiento",
        ).forEach { assertEquals(it, Intent.SAVE, ParkingIntentParser.parse(it)) }
    }

    @Test
    fun `where phrasings`() {
        listOf(
            "¿dónde aparqué?",
            "dónde está el coche",
            "dónde dejé el coche",
            "encuentra mi coche",
        ).forEach { assertEquals(it, Intent.WHERE, ParkingIntentParser.parse(it)) }
    }

    @Test
    fun `take me phrasings`() {
        listOf(
            "llévame al coche",
            "guíame al coche",
            "cómo vuelvo al coche",
            "llévame a donde aparqué",
        ).forEach { assertEquals(it, Intent.TAKE_ME, ParkingIntentParser.parse(it)) }
    }

    @Test
    fun `unrelated input`() {
        listOf(
            "llévame a casa",
            "busca un aparcamiento cerca",
            "¿cuánto queda?",
            "pon música",
        ).forEach { assertNull(it, ParkingIntentParser.parse(it)) }
    }
}
