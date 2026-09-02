package dev.pgm.roadmate.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQuestionParserTest {

    @Test
    fun `recognises "where am I" phrasings`() {
        listOf(
            "¿dónde estoy?",
            "dónde estamos",
            "¿qué calle es esta?",
            "¿en qué carretera voy?",
            "por dónde voy",
            "dime la calle",
            "cuál es mi ubicación",
        ).forEach { assertTrue(it, LocationQuestionParser.matches(it)) }
    }

    @Test
    fun `leaves routing and other questions alone`() {
        listOf(
            "¿cuánto queda para llegar?",
            "llévame a casa",
            "¿dónde hay una farmacia?",
            "pon música",
            "¿dónde aparqué?",
        ).forEach { assertFalse(it, LocationQuestionParser.matches(it)) }
    }
}
