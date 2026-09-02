package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.data.datasource.remote.WeatherDataSource
import dev.pgm.roadmate.domain.repository.WeatherRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Caches the "weather here" reading for a few minutes.
 *
 * Every spoken answer builds a prompt that may carry a weather line, so
 * without this every single question paid for a network round-trip to
 * OpenWeather before the model even saw the prompt — the opposite of an
 * offline-first assistant, and several hundred ms (or a timeout) of added
 * latency on each reply. Weather doesn't change on that timescale and the
 * driver doesn't move far in it either, so one reading serves a whole run of
 * questions. "¿Qué tiempo hace en <sitio>?" is a deliberate, explicit ask and
 * stays uncached.
 */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val weatherDataSource: WeatherDataSource
) : WeatherRepository {

    private val mutex = Mutex()
    private var cached: String? = null
    private var cachedAt: Pair<Double, Double>? = null
    private var cachedAtMs: Long = 0L

    override suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String? =
        mutex.withLock {
            val previous = cachedAt
            val fresh = System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
            val nearby = previous != null &&
                abs(previous.first - lat) < MOVED_DEGREES &&
                abs(previous.second - lon) < MOVED_DEGREES
            if (fresh && nearby) return@withLock cached

            val reading = weatherDataSource.getCurrentWeatherDescription(lat, lon)
            if (reading != null) {
                cached = reading
                cachedAt = lat to lon
                cachedAtMs = System.currentTimeMillis()
            }
            // A failed lookup doesn't invalidate a recent good one — say what
            // it was minutes ago rather than "no puedo consultar el tiempo".
            reading ?: cached?.takeIf { fresh }
        }

    override suspend fun getWeatherDescriptionFor(placeName: String): String? =
        weatherDataSource.getCurrentWeatherDescriptionFor(placeName)

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1_000L

        /** ~5 km: past that the reading is for somewhere else. */
        const val MOVED_DEGREES = 0.05
    }
}
