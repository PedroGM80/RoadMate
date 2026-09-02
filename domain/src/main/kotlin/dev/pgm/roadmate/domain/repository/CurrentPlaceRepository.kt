package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Where the driver is, as a readable label ("Calle Mayor · San Fernando"),
 * resolved entirely from the **downloaded offline map tiles** — no network,
 * no external geocoder. The map layer fills it in with [update] once it has
 * the tiles; the voice screen reads [label] for its location chip and falls
 * back to raw coordinates while it's null.
 */
interface CurrentPlaceRepository {
    val label: StateFlow<String?>
    fun update(label: String?)
}
