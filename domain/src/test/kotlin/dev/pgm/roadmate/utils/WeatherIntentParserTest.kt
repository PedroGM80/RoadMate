package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `pulls the place out of "en X" weather questions`() {
        mapOf(
            "¿qué tiempo hace en Ronda?" to "Ronda",
            "¿qué tiempo hace hoy en Sevilla?" to "Sevilla",
            "¿va a llover en Chiclana de la Frontera?" to "Chiclana de la Frontera",
            "el tiempo en Madrid" to "Madrid",
            "dime el tiempo en la Sierra de Cádiz" to "Sierra de Cádiz",
        ).forEach { (input, place) -> assertEquals(input, place, WeatherIntentParser.placeIn(input)) }
    }

    @Test
    fun `no place for "here" questions or time expressions`() {
        listOf(
            "¿qué tiempo hace?",
            "¿va a llover?",
            "¿va a llover en un rato?",
            "¿qué tiempo hará en 2 horas?",
        ).forEach { assertNull(it, WeatherIntentParser.placeIn(it)) }
    }
}
