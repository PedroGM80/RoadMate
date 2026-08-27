package dev.pgm.roadmate.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JokeProviderTest {

    @Test
    fun `detects joke intent regardless of case or surrounding words`() {
        assertTrue(JokeProvider.matchesJokeIntent("cuéntame un chiste"))
        assertTrue(JokeProvider.matchesJokeIntent("CUÉNTAME UN CHISTE BUENO"))
        assertTrue(JokeProvider.matchesJokeIntent("échame una broma"))
    }

    @Test
    fun `does not misfire on unrelated questions`() {
        assertFalse(JokeProvider.matchesJokeIntent("¿cuánto queda hasta el destino?"))
        assertFalse(JokeProvider.matchesJokeIntent("qué tiempo hace"))
    }

    @Test
    fun `randomJoke always returns a non-blank joke from the bank`() {
        repeat(20) {
            val joke = JokeProvider.randomJoke()
            assertTrue(joke.isNotBlank())
        }
    }
}
