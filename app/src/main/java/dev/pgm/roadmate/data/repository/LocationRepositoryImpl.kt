package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.data.datasource.local.LocationDataSource
import dev.pgm.roadmate.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the last known GPS fix for [CACHE_TTL_MS] to avoid hitting the fused
 * location provider on every prompt build.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDataSource: LocationDataSource
) : LocationRepository {

    private val _location = MutableStateFlow<Pair<Double, Double>?>(null)
    override val location: StateFlow<Pair<Double, Double>?> = _location.asStateFlow()

    private var cachedAtMs: Long = 0L

    override suspend fun getCurrentCoordinates(forceRefresh: Boolean): Pair<Double, Double>? {
        val cacheIsFresh = !forceRefresh && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
        if (cacheIsFresh && _location.value != null) return _location.value

        val fresh = locationDataSource.getCurrentLocation()
        if (fresh != null) {
            _location.value = fresh
            cachedAtMs = System.currentTimeMillis()
        }
        return fresh ?: _location.value
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1_000L
    }
}
