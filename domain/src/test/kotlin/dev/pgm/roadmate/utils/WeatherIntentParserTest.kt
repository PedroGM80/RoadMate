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
            "dime el tiempo meteorológico",
            "el tiempo meteorológico en Cádiz",
            "dime el tiempo de Cádiz",
            "¿qué tiempo hace en mi posición?",
        ).forEach { assertTrue(it, WeatherIntentParser.isWeatherQuestion(it)) }
    }

    @Test
    fun `does not fire on duration or unrelated uses of "tiempo"`() {
        listOf(
            "¿cuánto tiempo queda?",
            "¿llegamos a tiempo?",
            "necesito tiempo libre",
            "¿cuánto tiempo para llegar?",
            "el tiempo de viaje son dos horas",
            "el tiempo de espera es largo",
            "pon música",
            "llama a Ana",
        ).forEach { assertFalse(it, WeatherIntentParser.isWeatherQuestion(it)) }
    }

    @Test
    fun `pulls the place out of "en X" and "de X" weather questions`() {
        mapOf(
            "¿qué tiempo hace en Ronda?" to "Ronda",
            "¿qué tiempo hace hoy en Sevilla?" to "Sevilla",
            "¿va a llover en Chiclana de la Frontera?" to "Chiclana de la Frontera",
            "el tiempo en Madrid" to "Madrid",
            "dime el tiempo en la Sierra de Cádiz" to "Sierra de Cádiz",
            "dime el tiempo de Cádiz" to "Cádiz",
            "el tiempo meteorológico en Cádiz" to "Cádiz",
        ).forEach { (input, place) -> assertEquals(input, place, WeatherIntentParser.placeIn(input)) }
    }

    @Test
    fun `no place for "here" questions or time expressions`() {
        listOf(
            "¿qué tiempo hace?",
            "¿va a llover?",
            "¿va a llover en un rato?",
            "¿qué tiempo hará en 2 horas?",
            "el tiempo en mi posición",
            "dime el tiempo en mi zona",
            "el tiempo de hoy",
            "dime el tiempo meteorológico",
        ).forEach { assertNull(it, WeatherIntentParser.placeIn(it)) }
    }
}
