package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConversionParserTest {

    @Test
    fun `target-first phrasing`() {
        assertEquals("120 kilómetros son 74,6 millas.", UnitConversionParser.convert("cuántas millas son 120 km"))
        assertEquals(
            "100 kilómetros son 62,1 millas.",
            UnitConversionParser.convert("¿cuántas millas son cien kilómetros?"),
        )
    }

    @Test
    fun `value-first phrasing`() {
        assertEquals("3 galones son 11,4 litros.", UnitConversionParser.convert("pasa 3 galones a litros"))
        assertEquals("5 kilos son 11 libras.", UnitConversionParser.convert("convierte 5 kilos a libras"))
        assertEquals(
            "120 kilómetros son 74,6 millas.",
            UnitConversionParser.convert("cuánto son 120 km en millas"),
        )
    }

    @Test
    fun `temperature`() {
        assertEquals(
            "30 grados centígrados son 86 grados Fahrenheit.",
            UnitConversionParser.convert("30 grados centígrados a fahrenheit"),
        )
        assertEquals(
            "100 grados Fahrenheit son 37,8 grados centígrados.",
            UnitConversionParser.convert("cuántos grados centígrados son 100 fahrenheit"),
        )
    }

    @Test
    fun `not a conversion`() {
        listOf(
            "cuánto es doce entre cuatro",
            "llévame a casa",
            "¿cuánto queda para llegar?",
            "cuántas gasolineras hay cerca",
        ).forEach { assertNull(it, UnitConversionParser.convert(it)) }
    }
}
