package dev.pgm.roadmate.utils

import dev.pgm.roadmate.utils.MemoryCommandParser.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCommandParserTest {

    @Test
    fun `recognizes an explicit remember`() {
        assertEquals(
            Command.Remember("no me gustan las autovías"),
            MemoryCommandParser.parse("recuerda que no me gustan las autovías"),
        )
        assertEquals(
            Command.Remember("mi hija se llama Lucía"),
            MemoryCommandParser.parse("apunta que mi hija se llama Lucía"),
        )
    }

    @Test
    fun `maps prefiero into a preference`() {
        assertEquals(
            Command.Remember("prefiere las carreteras nacionales"),
            MemoryCommandParser.parse("prefiero las carreteras nacionales"),
        )
    }

    @Test
    fun `recognizes forget with a target`() {
        assertEquals(Command.Forget("las autovías"), MemoryCommandParser.parse("olvida lo de las autovías"))
    }

    @Test
    fun `recognizes a recall request`() {
        assertTrue(MemoryCommandParser.parse("¿qué sabes de mí?") is Command.Recall)
        assertTrue(MemoryCommandParser.parse("qué recuerdas de mí") is Command.Recall)
    }

    @Test
    fun `does not fire on ordinary sentences containing the verb`() {
        assertNull(MemoryCommandParser.parse("¿esto te recuerda a algo?"))
        assertNull(MemoryCommandParser.parse("cuéntame algo de este pueblo"))
    }

    @Test
    fun `recognizes set home and set work`() {
        assertEquals(Command.SetHome, MemoryCommandParser.parse("esta es mi casa"))
        assertEquals(Command.SetHome, MemoryCommandParser.parse("aquí vivo"))
        assertEquals(Command.SetWork, MemoryCommandParser.parse("aquí está mi trabajo"))
        assertEquals(Command.SetWork, MemoryCommandParser.parse("aquí trabajo"))
    }

    @Test
    fun `recognizes a relationship statement and lowercases the relation`() {
        assertEquals(
            Command.SetRelationship("hermano", "Juan"),
            MemoryCommandParser.parse("Juan es mi hermano"),
        )
        assertEquals(
            Command.SetRelationship("jefa", "Marta García"),
            MemoryCommandParser.parse("guarda a Marta García como mi jefa"),
        )
    }
}
