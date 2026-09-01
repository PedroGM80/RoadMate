package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceNameTest {

    @Test
    fun `strips a leading article, collapses space, lower-cases`() {
        assertEquals("gasolinera", PlaceName.normalize("una  Gasolinera"))
        assertEquals("hotel nh cádiz", PlaceName.normalize("  El Hotel NH Cádiz "))
    }

    @Test
    fun `voice search and pin tap converge on the same key`() {
        assertEquals(PlaceName.normalize("un mercadona"), PlaceName.normalize("Mercadona"))
    }

    @Test
    fun `caps the length`() {
        assertEquals(48, PlaceName.normalize("a".repeat(200)).length)
    }
}
