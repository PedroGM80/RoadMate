package dev.pgm.roadmate.utils

import dev.pgm.roadmate.utils.CalendarQuestionParser.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarQuestionParserTest {

    @Test
    fun today() {
        listOf(
            "¿qué tengo hoy?",
            "qué planes tengo hoy",
            "¿qué tengo esta tarde?",
            "mi agenda de hoy",
        ).forEach { assertEquals(it, Scope.TODAY, CalendarQuestionParser.parse(it)) }
    }

    @Test
    fun next() {
        listOf(
            "¿cuál es mi próxima cita?",
            "siguiente reunión",
            "a qué hora es mi cita",
        ).forEach { assertEquals(it, Scope.NEXT, CalendarQuestionParser.parse(it)) }
    }

    @Test
    fun unrelated() {
        listOf("¿cuánto queda?", "pon música", "llama a Ana", "¿qué tiempo hace?")
            .forEach { assertNull(it, CalendarQuestionParser.parse(it)) }
    }
}
