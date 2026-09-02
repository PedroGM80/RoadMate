package dev.pgm.roadmate.domain.model

/**
 * A kind of place the offline map can show without any network — it matches
 * these against POIs already baked into the downloaded vector tiles. Keep it
 * to categories the map schema actually tags; anything finer is a name
 * search, not a category.
 */
enum class PlaceCategory {
    FUEL,
    HOTEL,
    FOOD,
}
