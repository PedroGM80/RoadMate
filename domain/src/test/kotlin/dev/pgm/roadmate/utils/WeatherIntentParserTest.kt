package dev.pgm.roadmate.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherIntentParserTest {

    @Test
    fun `recognises weather phrasings`() {
        listOf(
            "¿qué tiempo hace?",
            "¿va a llover?",
            "¿cómo está el cielo?",
            "¿cuántos grados hay?",
            "dime la temperatura",
            "¿hace frío fuera?",
            "el tiempo para hoy",
            "¿está nublado?",
            "¿qué pronóstico hay?",
        ).forEach { assertTrue(it, WeatherIntentParser.isWeatherQuestion(it)) }
    }

    @Test
    fun `does not fire on duration or unrelated uses of "tiempo"`() {
        listOf(
            "¿cuánto tiempo queda?",
            "¿llegamos a tiempo?",
            "necesito tiempo libre",
            "¿cuánto tiempo para llegar?",
            "pon música",
            "llama a Ana",
        ).forEach { assertFalse(it, WeatherIntentParser.isWeatherQuestion(it)) }
    }
}
