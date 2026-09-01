package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArithmeticParserTest {

    @Test
    fun `multiplies number words`() {
        assertEquals("15 por 4 son 60.", ArithmeticParser.evaluate("cuánto es quince por cuatro"))
    }

    @Test
    fun `handles digits and the other operations`() {
        assertEquals("20 más 22 son 42.", ArithmeticParser.evaluate("¿cuánto son 20 más veintidós?"))
        assertEquals("100 menos 35 son 65.", ArithmeticParser.evaluate("cuánto es cien menos treinta y cinco"))
        assertEquals("12 entre 4 son 3.", ArithmeticParser.evaluate("cuánto es doce entre cuatro"))
        assertEquals("7 entre 2 son 3,5.", ArithmeticParser.evaluate("cuánto es siete dividido entre dos"))
    }

    @Test
    fun `guards divide by zero`() {
        assertEquals("No se puede dividir entre cero.", ArithmeticParser.evaluate("cuánto es cinco entre cero"))
    }

    @Test
    fun `returns null for non-arithmetic or unparseable numbers`() {
        assertNull(ArithmeticParser.evaluate("cuál es la capital de Francia"))
        assertNull(ArithmeticParser.evaluate("cuánto es un millón por dos"))
        assertNull(ArithmeticParser.evaluate("cuéntame un chiste"))
    }
}
