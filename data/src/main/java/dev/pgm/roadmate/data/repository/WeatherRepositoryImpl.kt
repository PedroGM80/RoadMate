package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.data.datasource.remote.WeatherDataSource
import dev.pgm.roadmate.domain.repository.WeatherRepository
import javax.inject.Inject

class WeatherRepositoryImpl @Inject constructor(
    private val weatherDataSource: WeatherDataSource
) : WeatherRepository {

    override suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String? =
        weatherDataSource.getCurrentWeatherDescription(lat, lon)

    override suspend fun getWeatherDescriptionFor(placeName: String): String? =
        weatherDataSource.getCurrentWeatherDescriptionFor(placeName)
}
