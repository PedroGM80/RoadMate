package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionPunctuationTest {

    @Test
    fun `wraps an obvious question`() {
        assertEquals("¿cuántos kilómetros faltan?", QuestionPunctuation.normalize("cuántos kilómetros faltan"))
        assertEquals("¿qué hora es?", QuestionPunctuation.normalize("qué hora es"))
        assertEquals("¿dónde hay una farmacia?", QuestionPunctuation.normalize("dónde hay una farmacia"))
        assertEquals("¿puedo aparcar aquí?", QuestionPunctuation.normalize("puedo aparcar aquí"))
    }

    @Test
    fun `leaves statements and commands alone`() {
        assertEquals("pon música", QuestionPunctuation.normalize("pon música"))
        assertEquals("llama a Ana", QuestionPunctuation.normalize("llama a Ana"))
        assertEquals("recuérdame que prefiero las nacionales", QuestionPunctuation.normalize("recuérdame que prefiero las nacionales"))
    }

    @Test
    fun `does not double-punctuate`() {
        assertEquals("¿qué hora es?", QuestionPunctuation.normalize("¿qué hora es?"))
        assertEquals("qué hora es?", QuestionPunctuation.normalize("qué hora es?"))
    }
}
