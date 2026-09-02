package dev.pgm.roadmate.domain.repository

/**
 * Turns the driver's coordinate into something readable for the location
 * chip — "Calle Mayor, San Fernando · Cádiz". Best-effort and offline where
 * the platform geocoder has local data; returns null when it can't resolve,
 * and the UI falls back to the raw lat/lon.
 */
interface ReverseGeocodeRepository {
    suspend fun describe(lat: Double, lon: Double): String?
}
