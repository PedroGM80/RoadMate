package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StylePreferenceParserTest {

    @Test
    fun `recognizes a request for shorter answers`() {
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("respuestas cortas"))
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("dame respuestas más breves"))
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("de ahora en adelante respuestas resumidas"))
    }

    @Test
    fun `recognizes a request for more detail`() {
        assertEquals(AnswerStyle.DETAILED, StylePreferenceParser.parse("respuestas con más detalle"))
        assertEquals(AnswerStyle.DETAILED, StylePreferenceParser.parse("quiero respuestas largas"))
    }

    @Test
    fun `recognizes a request to go back to normal`() {
        assertEquals(AnswerStyle.NORMAL, StylePreferenceParser.parse("respuestas normales"))
        assertEquals(AnswerStyle.NORMAL, StylePreferenceParser.parse("pon las respuestas estándar"))
    }

    @Test
    fun `leaves ordinary questions alone`() {
        assertNull(StylePreferenceParser.parse("¿cuánto queda para llegar?"))
        assertNull(StylePreferenceParser.parse("cuéntame algo corto de este pueblo"))
        assertNull(StylePreferenceParser.parse("busca una gasolinera"))
    }
}
