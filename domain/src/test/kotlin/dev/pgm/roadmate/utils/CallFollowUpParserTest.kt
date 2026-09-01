package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel
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

    private val oneContactTwoNumbers = listOf(
        ContactMatch("Ana", "600111222", PhoneLabel.MOBILE),
        ContactMatch("Ana", "955000000", PhoneLabel.WORK),
    )

    @Test
    fun `resolves a number label`() {
        assertEquals(
            oneContactTwoNumbers[0],
            CallFollowUpParser.resolve("el móvil", oneContactTwoNumbers),
        )
        assertEquals(
            oneContactTwoNumbers[1],
            CallFollowUpParser.resolve("la del trabajo", oneContactTwoNumbers),
        )
        assertEquals(
            oneContactTwoNumbers[1],
            CallFollowUpParser.resolve("la de la oficina", oneContactTwoNumbers),
        )
    }

    @Test
    fun `label follow-up falls through when no candidate carries it`() {
        assertNull(CallFollowUpParser.resolve("la de casa", oneContactTwoNumbers))
    }

    @Test
    fun `returns null when the follow-up is ambiguous or unrelated`() {
        assertNull(CallFollowUpParser.resolve("Ana", candidates))       // matches all three
        assertNull(CallFollowUpParser.resolve("cuéntame un chiste", candidates))
        assertNull(CallFollowUpParser.resolve("no sé", candidates))
    }
}
