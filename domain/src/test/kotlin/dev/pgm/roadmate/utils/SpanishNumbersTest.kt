package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpanishNumbersTest {

    @Test
    fun `words and digits`() {
        mapOf(
            "cero" to 0.0,
            "quince" to 15.0,
            "treinta y cinco" to 35.0,
            "cien" to 100.0,
            "ciento veinte" to 120.0,
            "doscientas" to 200.0,
            "trescientos cuarenta y dos" to 342.0,
            "mil" to 1000.0,
            "dos mil quinientos" to 2500.0,
            "tres mil quinientos cincuenta" to 3550.0,
            "120" to 120.0,
            "3,5" to 3.5,
        ).forEach { (w, n) -> assertEquals(w, n, SpanishNumbers.parse(w)) }
    }

    @Test
    fun `not a number`() {
        listOf("", "hola", "un millón", "gasolinera").forEach { assertNull(it, SpanishNumbers.parse(it)) }
    }

    @Test
    fun `spoken form`() {
        assertEquals("60", SpanishNumbers.spoken(60.0))
        assertEquals("3,5", SpanishNumbers.spoken(3.5))
        assertEquals("74,6", SpanishNumbers.spoken(74.6))
    }
}
