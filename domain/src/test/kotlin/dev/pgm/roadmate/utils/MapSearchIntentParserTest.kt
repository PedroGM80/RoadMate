package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapSearchIntentParserTest {

    @Test
    fun `pulls the place out of a find phrase and trims trailing filler`() {
        assertEquals("gasolineras", MapSearchIntentParser.extractSearchQuery("busca gasolineras"))
        assertEquals("un hotel", MapSearchIntentParser.extractSearchQuery("encuentra un hotel cerca de mí"))
        assertEquals("una farmacia", MapSearchIntentParser.extractSearchQuery("dónde hay una farmacia"))
    }

    @Test
    fun `does not send a fact lookup to the maps app`() {
        assertNull(MapSearchIntentParser.extractSearchQuery("busca información sobre la Alhambra"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca en internet quién ganó la liga"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca cómo se hace una tortilla"))
    }

    @Test
    fun `ignores unrelated sentences`() {
        assertNull(MapSearchIntentParser.extractSearchQuery("¿cuánto queda para llegar?"))
        assertNull(MapSearchIntentParser.extractSearchQuery("pon música"))
    }
}
