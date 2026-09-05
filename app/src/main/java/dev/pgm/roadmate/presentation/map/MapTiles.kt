package dev.pgm.roadmate.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.gson.JsonPrimitive
import dev.pgm.roadmate.R
import dev.pgm.roadmate.ml.DebugTrace
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.hypot

internal const val PIN_PREFIX = "roadmate-pin-"

/** Icon key for a name-search result (no category colour). */
internal const val NAME_PIN = "NAME"

/** Tile POI name properties, most specific first. */
internal val NAME_PROPS = arrayOf("name:es", "name", "name:latin", "name_int")

/** Throttles tile reverse-geocoding to "moved enough, and not too often". */
internal class GeoThrottle {
    var at: Pair<Double, Double>? = null
    var whenMs = 0L
}

/** Lower-case and strip accents so "jesus" matches "Jesús". */
internal fun foldForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()


/** Rough planar distance in metres between two (lat, lon) points. */
internal fun metersBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val mx = (b.second - a.second) * cos(Math.toRadians(a.first)) * 111_320.0
    val my = (b.first - a.first) * 110_540.0
    return hypot(mx, my)
}

/** Distance in metres from the origin (0,0) to segment a→b (both in local metres). */
internal fun segMeters(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    if (dx == 0.0 && dy == 0.0) return hypot(ax, ay)
    val t = (((-ax) * dx + (-ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

internal val ROAD_SRC_LAYERS = arrayOf("transportation_name", "transportation")
internal val PLACE_SRC_LAYERS = arrayOf("place")
internal val LOCALITY_CLASSES = setOf(
    "city", "town", "village", "suburb", "hamlet", "neighbourhood", "quarter", "locality",
)

/**
 * Resolve the driver's position to "calle · localidad" from the downloaded
 * tiles and publish it for the Voz screen's chip. Throttled: only when the
 * driver has moved ~40 m and at most every 8 s. Called from the camera-idle
 * listener (tiles are parsed by then) and a startup fallback loop.
 */
internal fun resolvePlaceLabel(map: MapLibreMap, viewModel: MapViewModel, throttle: GeoThrottle) {
    val here = map.currentLatLon() ?: return
    val now = System.currentTimeMillis()
    val moved = throttle.at?.let { metersBetween(it, here) > 40.0 } ?: true
    if (!moved || now - throttle.whenMs < 8_000L) return
    throttle.at = here
    throttle.whenMs = now
    val label = runCatching { placeFromTiles(map, here.first, here.second) }
        .onFailure { DebugTrace.log("geo: threw ${it.message}") }
        .getOrNull()
    DebugTrace.log("geo: ${here.first},${here.second} -> ${label ?: "null"}")
    viewModel.onPlaceResolved(label)
}

/**
 * Reverse-geocode a coordinate from the loaded offline tiles: the nearest
 * named road within ~130 m and the nearest town/locality label. Reads the
 * vector *source* (like the POI query) so it resolves at driving zoom, and
 * runs entirely on-device — no network geocoder.
 */
internal fun placeFromTiles(map: MapLibreMap, atLat: Double, atLon: Double): String? {
    val src = map.style?.sources?.firstOrNull { it is VectorSource } as? VectorSource ?: return null
    val cosLat = cos(Math.toRadians(atLat))
    fun toM(lat: Double, lon: Double): Pair<Double, Double> =
        (lon - atLon) * cosLat * 111_320.0 to (lat - atLat) * 110_540.0

    var road: String? = null
    var roadM = 130.0
    runCatching { src.querySourceFeatures(ROAD_SRC_LAYERS, null) }
        .getOrDefault(emptyList())
        .forEach { f ->
            val name = f.getStringProperty("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val lines: List<List<Point>> = when (val g = f.geometry()) {
                is LineString -> listOf(g.coordinates())
                is MultiLineString -> g.coordinates()
                else -> return@forEach
            }
            for (line in lines) for (i in 0 until line.size - 1) {
                val (ax, ay) = toM(line[i].latitude(), line[i].longitude())
                val (bx, by) = toM(line[i + 1].latitude(), line[i + 1].longitude())
                val d = segMeters(ax, ay, bx, by)
                if (d < roadM) { roadM = d; road = name }
            }
        }

    var locality: String? = null
    var localityM = Double.MAX_VALUE
    runCatching { src.querySourceFeatures(PLACE_SRC_LAYERS, null) }
        .getOrDefault(emptyList())
        .forEach { f ->
            val cls = f.getStringProperty("class")
            if (cls != null && cls !in LOCALITY_CLASSES) return@forEach
            val p = f.geometry() as? Point ?: return@forEach
            val name = f.getStringProperty("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val (mx, my) = toM(p.latitude(), p.longitude())
            val d = hypot(mx, my)
            if (d < localityM) { localityM = d; locality = name }
        }

    DebugTrace.log(
        "geo: road=${road ?: "-"}(${roadM.toInt()}m) locality=${locality ?: "-"}",
    )
    return listOfNotNull(road, locality).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}


internal fun refreshPois(
    map: MapLibreMap,
    mapView: MapView,
    manager: SymbolManager,
    kind: PoiKind?,
    nameQuery: String?,
): List<LatLng> {
    return runCatching {
        val needle = nameQuery?.let(::foldForSearch).takeUnless { it.isNullOrBlank() }
        if (kind == null && needle == null) {
            manager.deleteAll()
            return emptyList()
        }

        val vectorSource = map.style?.sources?.firstOrNull { it is VectorSource } as? VectorSource
        // A name search also looks at the "place" layer (towns, villages,
        // neighbourhoods) — "llévame a Chiclana" is a place, not a POI.
        val layers = if (kind == null) {
            arrayOf("poi", "poi_label", "place")
        } else {
            arrayOf("poi", "poi_label")
        }
        val points = vectorSource
            ?.querySourceFeatures(layers, null)
            .orEmpty()
            .filter { it.geometry() is Point }

        val iconImage = PIN_PREFIX + (kind?.name ?: NAME_PIN)
        val fallbackLabel = kind?.let { fallbackLabelFor(mapView.context, it) } ?: nameQuery.orEmpty()
        val matches = if (kind != null) {
            points.filter { it.getStringProperty("class") in kind.classes }
        } else {
            // Best match first: a place whose name *is* the query beats a shop
            // that merely contains the word ("Chiclana" vs "Bahía de Chiclana").
            points
                .mapNotNull { f -> nameMatchScore(f, needle!!)?.let { f to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        }

        DebugTrace.log(
            "POI ${kind?.name ?: "name:$needle"}: source=${vectorSource?.id} pois=${points.size} " +
                "matching=${matches.size}; classes = " +
                points.mapNotNull { it.getStringProperty("class") }.groupingBy { it }.eachCount(),
        )

        val seen = HashSet<String>()
        val pins = matches.asSequence()
            .mapNotNull { f ->
                val p = f.geometry() as Point
                val at = LatLng(p.latitude(), p.longitude())
                if (!seen.add("${p.latitude()},${p.longitude()}")) return@mapNotNull null
                val name = f.getStringProperty("name")?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: fallbackLabel
                // Icon only on the map — a text field needs a glyph font the
                // OpenFreeMap style may not serve, which silently drops the
                // whole symbol. The name rides along in `data` for the sheet.
                at to SymbolOptions()
                    .withLatLng(at)
                    .withIconImage(iconImage)
                    .withIconSize(1.0f)
                    .withData(JsonPrimitive(name))
            }
            .take(120)
            .toList()
        // Only swap the pins when this query actually found some — zooming out
        // past the POI vector layer's min zoom (~14) returns nothing, and we
        // don't want that to wipe the pins already on screen.
        if (pins.isNotEmpty()) {
            manager.deleteAll()
            manager.create(pins.map { it.second })
        }
        pins.map { it.first }
    }.getOrDefault(emptyList())
}

/** "place"-layer classes that count as somewhere you'd drive *to*. */
internal val PLACE_LAYER_CLASSES = setOf(
    "city", "town", "village", "hamlet", "suburb", "neighbourhood", "quarter",
    "municipality", "isolated_dwelling", "locality",
)

/**
 * How well a tile feature's name matches the spoken query, or null if it
 * doesn't. Exact match ranks highest, then prefix, then a whole-word hit,
 * then a bare substring; a town/village gets a bump over a like-named shop.
 */
internal fun nameMatchScore(feature: Feature, needle: String): Int? {
    val name = NAME_PROPS.firstNotNullOfOrNull { feature.getStringProperty(it) }
        ?.let(::foldForSearch)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    var score = when {
        name == needle -> 100
        name.startsWith("$needle ") || name.startsWith("$needle,") -> 70
        Regex("\\b${Regex.escape(needle)}\\b").containsMatchIn(name) -> 40
        name.contains(needle) -> 10
        else -> return null
    }
    if (feature.getStringProperty("class") in PLACE_LAYER_CLASSES) score += 25
    return score
}

internal fun fallbackLabelFor(context: Context, kind: PoiKind): String = when (kind) {
    PoiKind.FUEL -> context.getString(R.string.map_poi_fuel_one)
    PoiKind.HOTEL -> context.getString(R.string.map_poi_hotel_one)
    PoiKind.FOOD -> context.getString(R.string.map_poi_food_one)
}

/**
 * @param sizeDp how big the pin bitmap is. The phone's default reads well at
 *   arm's length; the car screen is further away and lower-resolution, and a
 *   32dp pin there comes out as a speck among the base style's own icons.
 */
internal fun registerPinIcons(style: Style, context: Context, density: Float, sizeDp: Float = 32f) {
    val size = (sizeDp * density).toInt()
    val cx = size / 2f
    val ring = 2.5f * density

    fun pin(fill: Int, iconRes: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // A stronger drop shadow than the phone's — the car display is further
        // from the eye and often glare-lit.
        paint.color = 0x55000000
        canvas.drawCircle(cx, cx + 1.5f * density, cx - ring, paint)
        paint.color = 0xFFFFFFFF.toInt() // crisp white ring
        canvas.drawCircle(cx, cx, cx - ring, paint)
        paint.color = fill // category disc
        canvas.drawCircle(cx, cx, cx - ring * 2.0f, paint)

        ContextCompat.getDrawable(context, iconRes)?.mutate()?.let { d ->
            d.setTint(0xFFFFFFFF.toInt())
            val pad = (size * 0.25f).toInt()
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(canvas)
        }
        return bitmap
    }

    PoiKind.entries.forEach { kind ->
        style.addImage(PIN_PREFIX + kind.name, pin(kind.tint, kind.iconRes))
    }
    // Neutral pin for name searches ("busca el Mercadona") — no category tint.
    style.addImage(PIN_PREFIX + NAME_PIN, pin(0xFF455A64.toInt(), R.drawable.lucide_ic_map_pin))
}

