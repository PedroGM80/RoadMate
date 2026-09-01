package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallFollowUpParserTest {

    private val candidates = listOf(
        ContactMatch("Ana García", "600111222"),
        ContactMatch("Ana López", "600333444"),
        ContactMatch("Ana Ruiz", "600555666"),
    )

    @Test
    fun `resolves an ordinal`() {
        assertEquals(candidates[0], CallFollowUpParser.resolve("la primera", candidates))
        assertEquals(candidates[1], CallFollowUpParser.resolve("el segundo", candidates))
        assertEquals(candidates[2], CallFollowUpParser.resolve("la última", candidates))
    }

    @Test
    fun `resolves a distinguishing surname`() {
        assertEquals(candidates[1], CallFollowUpParser.resolve("Ana López", candidates))
        assertEquals(candidates[0], CallFollowUpParser.resolve("la García", candidates))
    }

    @Test
    fun `returns null when the follow-up is ambiguous or unrelated`() {
        assertNull(CallFollowUpParser.resolve("Ana", candidates))       // matches all three
        assertNull(CallFollowUpParser.resolve("cuéntame un chiste", candidates))
        assertNull(CallFollowUpParser.resolve("no sé", candidates))
    }
}
