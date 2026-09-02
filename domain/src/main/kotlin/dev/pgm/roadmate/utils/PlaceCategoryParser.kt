package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.PlaceCategory

/**
 * Maps a spoken place query ("gasolineras", "un sitio para comer", "dónde
 * dormir") to a [PlaceCategory] the offline map can pin, or null when the
 * query names a specific place instead of a category. The text handed in is
 * whatever [MapSearchIntentParser] already pulled out of the utterance, so
 * this only has to look at the place words themselves.
 */
object PlaceCategoryParser {

    private val FUEL = Regex(
        """\b(?:gasolinera|gasolineras|gasolinería|estaci[oó]n(?:es)?\s+de\s+servicio|""" +
            """combustible|carburante|repostar|repostaje|gasoil|gas[oó]leo|di[eé]sel|""" +
            """surtidor|echar\s+gasolina|poner\s+gasolina)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val HOTEL = Regex(
        """\b(?:hotel|hoteles|hostal|hostales|alojamiento|alojamientos|""" +
            """d[oó]nde\s+dormir|sitio\s+para\s+dormir|pensi[oó]n|pensiones|""" +
            """motel|moteles|casa\s+rural|albergue)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FOOD = Regex(
        """\b(?:restaurante|restaurantes|sitio\s+para\s+comer|d[oó]nde\s+comer|""" +
            """algo\s+de\s+comer|un\s+bar|bares|cafeter[ií]a|cafeter[ií]as|caf[eé]|""" +
            """comida\s+r[aá]pida|hamburgueser[ií]a|pizzer[ií]a|tapas|""" +
            """men[uú]\s+del\s+d[ií]a)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(query: String): PlaceCategory? {
        val text = query.trim()
        return when {
            FUEL.containsMatchIn(text) -> PlaceCategory.FUEL
            HOTEL.containsMatchIn(text) -> PlaceCategory.HOTEL
            FOOD.containsMatchIn(text) -> PlaceCategory.FOOD
            else -> null
        }
    }
}
