package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the accent handling across the parsers.
 *
 * All of this was broken at once, and for one reason: Java's regex engine
 * defines `\b`, `\w` and `\W` over ASCII unless told otherwise, so every
 * pattern that touched an accented letter silently failed. "qué" — the most
 * common way a Spanish driver starts a question — never matched anything.
 * These tests exist so it can't quietly come back with the next pattern
 * somebody adds.
 */
class SpanishRegexTest {

    @Test
    fun `a word boundary works after an accent`() {
        val r = spanishRegex("""^qu[eé]\b""")
        assertTrue(r.containsMatchIn("qué hora es"))
        assertTrue(r.containsMatchIn("que hora es"))
        // Still a boundary: "queda" is not "que".
        assertTrue(!r.containsMatchIn("queda mucho"))
    }

    @Test
    fun `a word boundary works before an accent`() {
        val r = spanishRegex("""\b[uú]ltim[oa]\b""")
        assertTrue(r.containsMatchIn("la última"))
        assertTrue(r.containsMatchIn("el ultimo"))
    }

    @Test
    fun `case folding covers accented letters`() {
        assertTrue(spanishRegex("""^qu[eé]\b""").containsMatchIn("QUÉ HORA ES"))
    }

    @Test
    fun `splitting on non-word characters keeps accented words whole`() {
        assertEquals(
            listOf("ana", "garcía", "ruiz"),
            "ana garcía ruiz".split(spanishRegex("""\W+""", ignoreCase = false)),
        )
    }

    @Test
    fun `accented questions get punctuated`() {
        assertEquals("¿qué hora es?", QuestionPunctuation.normalize("qué hora es"))
        assertEquals("¿por qué vamos por aquí?", QuestionPunctuation.normalize("por qué vamos por aquí"))
        assertEquals("¿quién es?", QuestionPunctuation.normalize("quién es"))
        assertEquals("¿cómo se llama?", QuestionPunctuation.normalize("cómo se llama"))
        assertEquals("¿cuándo llegamos?", QuestionPunctuation.normalize("cuándo llegamos"))
    }

    @Test
    fun `an accented fact lookup is not sent to the map`() {
        assertNull(MapSearchIntentParser.extractSearchQuery("busca qué significa efímero"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca por qué el cielo es azul"))
        // …but a real place still is.
        assertEquals("una gasolinera", MapSearchIntentParser.extractSearchQuery("busca una gasolinera"))
    }

    @Test
    fun `an accented ordinal picks the right contact`() {
        val candidates = listOf(
            ContactMatch("Ana Ruiz", "600555666", PhoneLabel.OTHER),
            ContactMatch("Ana Soler", "600777888", PhoneLabel.OTHER),
        )
        assertEquals(candidates[1], CallFollowUpParser.resolve("la última", candidates))
        assertEquals(candidates[1], CallFollowUpParser.resolve("la segunda", candidates))
    }

    @Test
    fun `an accented surname picks the right contact`() {
        val candidates = listOf(
            ContactMatch("Ana Ruiz", "600555666", PhoneLabel.OTHER),
            ContactMatch("Ana García", "600777888", PhoneLabel.OTHER),
        )
        assertNotNull(CallFollowUpParser.resolve("García", candidates))
        assertEquals(candidates[1], CallFollowUpParser.resolve("García", candidates))
    }
}
