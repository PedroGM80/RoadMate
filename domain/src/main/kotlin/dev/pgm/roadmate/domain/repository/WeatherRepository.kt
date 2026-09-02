package dev.pgm.roadmate.domain.repository

/**
 * Contract for the one piece of RoadMate allowed to need internet: current
 * weather for a coordinate. Returns null whenever it's unavailable (no
 * connection, no API key configured, request failure) so callers can simply
 * omit it rather than branch on a Result/exception.
 */
interface WeatherRepository {
    suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String?

    /**
     * Weather for a place the driver named out loud ("¿qué tiempo hace en
     * Sevilla?"). The place name is resolved by the weather service itself,
     * so this works for any town without a downloaded map. Null when it
     * can't be resolved or fetched.
     */
    suspend fun getWeatherDescriptionFor(placeName: String): String?
}
