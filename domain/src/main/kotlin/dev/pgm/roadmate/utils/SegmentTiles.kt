package dev.pgm.roadmate.utils

import kotlin.math.floor

/**
 * BRouter serves its offline routing data as `.rd5` "segment" files on a
 * 5°×5° grid, each named after its south-west corner: `W5_N40` covers
 * lon −5..0, lat 40..45. This maps a coordinate (and a route's endpoints)
 * to the tile name(s) that cover it. Pure — no Android.
 */
object SegmentTiles {

    private const val STEP = 5

    /** The `.rd5` file name (no extension) whose 5° cell contains [lat],[lon]. */
    fun nameFor(lat: Double, lon: Double): String {
        val lonCell = (floor(lon / STEP) * STEP).toInt()
        val latCell = (floor(lat / STEP) * STEP).toInt()
        val lonPart = if (lonCell < 0) "W${-lonCell}" else "E$lonCell"
        val latPart = if (latCell < 0) "S${-latCell}" else "N$latCell"
        return "${lonPart}_${latPart}"
    }

    /** The distinct tiles needed to route between the given points. */
    fun namesFor(vararg points: Pair<Double, Double>): List<String> =
        points.map { (lat, lon) -> nameFor(lat, lon) }.distinct()
}
