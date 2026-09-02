package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCategoryParserTest {

    @Test
    fun `recognises fuel phrasings`() {
        assertEquals(PlaceCategory.FUEL, PlaceCategoryParser.parse("gasolineras"))
        assertEquals(PlaceCategory.FUEL, PlaceCategoryParser.parse("una gasolinera"))
        assertEquals(PlaceCategory.FUEL, PlaceCategoryParser.parse("dónde puedo repostar"))
        assertEquals(PlaceCategory.FUEL, PlaceCategoryParser.parse("necesito gasoil"))
    }

    @Test
    fun `recognises hotel phrasings`() {
        assertEquals(PlaceCategory.HOTEL, PlaceCategoryParser.parse("un hotel"))
        assertEquals(PlaceCategory.HOTEL, PlaceCategoryParser.parse("hostales"))
        assertEquals(PlaceCategory.HOTEL, PlaceCategoryParser.parse("un sitio para dormir"))
    }

    @Test
    fun `recognises food phrasings`() {
        assertEquals(PlaceCategory.FOOD, PlaceCategoryParser.parse("un restaurante"))
        assertEquals(PlaceCategory.FOOD, PlaceCategoryParser.parse("algo de comer"))
        assertEquals(PlaceCategory.FOOD, PlaceCategoryParser.parse("una cafetería"))
        assertEquals(PlaceCategory.FOOD, PlaceCategoryParser.parse("un bar"))
    }

    @Test
    fun `returns null for a specific place name`() {
        assertNull(PlaceCategoryParser.parse("el Mercadona"))
        assertNull(PlaceCategoryParser.parse("la Sagrada Familia"))
        assertNull(PlaceCategoryParser.parse("calle Mayor 5"))
        assertNull(PlaceCategoryParser.parse("el hospital de la Paz"))
        assertNull(PlaceCategoryParser.parse("casa"))
        assertNull(PlaceCategoryParser.parse("el aeropuerto"))
    }

    @Test
    fun `picks the first category when a query could touch two`() {
        // "restaurante" + "hotel" — FUEL check runs first but doesn't match,
        // then HOTEL wins over FOOD by order.
        assertEquals(
            PlaceCategory.HOTEL,
            PlaceCategoryParser.parse("un hotel con restaurante"),
        )
    }

    @Test
    fun `is case- and article-insensitive`() {
        assertEquals(PlaceCategory.FUEL, PlaceCategoryParser.parse("GASOLINERA"))
        assertEquals(PlaceCategory.FOOD, PlaceCategoryParser.parse("Una Cafetería"))
    }
}
