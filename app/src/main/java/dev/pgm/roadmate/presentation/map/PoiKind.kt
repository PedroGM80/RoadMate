package dev.pgm.roadmate.presentation.map

import androidx.annotation.ColorInt

/**
 * The POI categories the in-app map can pin. Matching is done against the
 * `class` property of features already in the rendered vector tiles
 * (OpenMapTiles / OpenFreeMap schema) — no network, no places API.
 */
enum class PoiKind(
    val label: String,
    val classes: Set<String>,
    @ColorInt val tint: Int,
) {
    FUEL("Gasolineras", setOf("fuel"), 0xFF2E7D32.toInt()),
    HOTEL("Hoteles", setOf("hotel", "hostel", "motel", "guest_house"), 0xFF1565C0.toInt()),
    FOOD("Comida", setOf("restaurant", "fast_food", "cafe", "bar", "pub", "food_court"), 0xFFE65100.toInt()),
}
