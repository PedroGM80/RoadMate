package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTilesTest {

    @Test
    fun `names the 5-degree cell by its south-west corner`() {
        // Madrid
        assertEquals("W5_N40", SegmentTiles.nameFor(lat = 40.4168, lon = -3.7038))
        // Barcelona
        assertEquals("E0_N40", SegmentTiles.nameFor(lat = 41.3874, lon = 2.1686))
        // Cádiz
        assertEquals("W10_N35", SegmentTiles.nameFor(lat = 36.5271, lon = -6.2886))
    }

    @Test
    fun `handles the southern and western hemispheres`() {
        assertEquals("W60_S35", SegmentTiles.nameFor(lat = -34.6, lon = -58.4)) // Buenos Aires
        assertEquals("E15_S35", SegmentTiles.nameFor(lat = -33.9, lon = 18.4)) // Cape Town
    }

    @Test
    fun `de-duplicates tiles shared by both endpoints`() {
        val one = SegmentTiles.namesFor(40.4 to -3.7, 40.5 to -3.6)
        assertEquals(listOf("W5_N40"), one)

        val two = SegmentTiles.namesFor(40.4 to -3.7, 41.4 to 2.2)
        assertEquals(listOf("W5_N40", "E0_N40"), two)
    }
}
