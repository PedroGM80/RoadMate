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
    fun `handles more find phrasings and standalone proximity filler`() {
        assertEquals("un cajero", MapSearchIntentParser.extractSearchQuery("busco un cajero por aquí"))
        assertEquals("farmacia", MapSearchIntentParser.extractSearchQuery("hay alguna farmacia cerca"))
        assertEquals("el cajero", MapSearchIntentParser.extractSearchQuery("dónde queda el cajero más cercano"))
        assertEquals("un restaurante", MapSearchIntentParser.extractSearchQuery("busca un restaurante en la zona"))
        assertEquals("la gasolinera", MapSearchIntentParser.extractSearchQuery("dónde está la gasolinera más cercana"))
    }

    @Test
    fun `treats navigation phrasings as a place, even when they start with como`() {
        assertEquals("la playa", MapSearchIntentParser.extractSearchQuery("llévame a la playa"))
        assertEquals("hospital", MapSearchIntentParser.extractSearchQuery("cómo llego al hospital"))
        assertEquals("el aeropuerto", MapSearchIntentParser.extractSearchQuery("guíame hasta el aeropuerto"))
        assertEquals("casa", MapSearchIntentParser.extractSearchQuery("llévame a casa"))
    }

    @Test
    fun `does not send a fact lookup to the maps app`() {
        assertNull(MapSearchIntentParser.extractSearchQuery("busca información sobre la Alhambra"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca en internet quién ganó la liga"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca cómo se hace una tortilla"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca quién descubrió América"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca cuánto cuesta un iPhone"))
        assertNull(MapSearchIntentParser.extractSearchQuery("busca qué significa efímero"))
    }

    @Test
    fun `ignores unrelated sentences`() {
        assertNull(MapSearchIntentParser.extractSearchQuery("¿cuánto queda para llegar?"))
        assertNull(MapSearchIntentParser.extractSearchQuery("pon música"))
        assertNull(MapSearchIntentParser.extractSearchQuery("voy a llegar tarde"))
    }
}
