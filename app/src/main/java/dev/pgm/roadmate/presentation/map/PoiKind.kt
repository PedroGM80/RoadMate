package dev.pgm.roadmate.presentation.map

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import dev.pgm.roadmate.R

/**
 * The POI categories the in-app map can pin. Matching is done against the
 * `class` property of features already in the rendered vector tiles
 * (OpenMapTiles / OpenFreeMap schema) — no network, no places API.
 */
enum class PoiKind(
    @StringRes val labelRes: Int,
    val classes: Set<String>,
    @ColorInt val tint: Int,
) {
    FUEL(R.string.map_poi_fuel, setOf("fuel"), 0xFF2E7D32.toInt()),
    // OpenFreeMap tags hotels/hostels as `lodging`.
    HOTEL(
        R.string.map_poi_hotel,
        setOf("lodging", "hotel", "hostel", "motel", "guest_house"),
        0xFF1565C0.toInt(),
    ),
    FOOD(
        R.string.map_poi_food,
        setOf("restaurant", "fast_food", "cafe", "bar", "pub", "food_court"),
        0xFFE65100.toInt(),
    ),
}
