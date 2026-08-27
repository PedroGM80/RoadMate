package dev.pgm.roadmate.data.datasource.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.pgm.roadmate.data.di.OpenWeatherApiKey
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

@JsonClass(generateAdapter = true)
internal data class WeatherResponse(
    val weather: List<WeatherDescription>,
    val main: WeatherMain
)

@JsonClass(generateAdapter = true)
internal data class WeatherDescription(val description: String)

@JsonClass(generateAdapter = true)
internal data class WeatherMain(val temp: Double)

private interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "es"
    ): WeatherResponse
}

/**
 * Fetches current weather from OpenWeather — the one piece of RoadMate that
 * requires internet, matching the "funciona sin internet (excepto weather)"
 * requirement. Returns null on any failure (no connection, missing/invalid
 * key, GPS unavailable) instead of throwing, so PromptBuilder can simply
 * omit weather when it's not available.
 *
 * [apiKey] comes from BuildConfig.OPENWEATHER_API_KEY, itself sourced from
 * local.properties (gitignored) at build time — see :data's build.gradle.kts.
 * Empty by default, in which case this always returns null without making a
 * network call.
 */
class WeatherDataSource @Inject constructor(
    @OpenWeatherApiKey private val apiKey: String
) {

    private val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(OpenWeatherApi::class.java)
    }

    suspend fun getCurrentWeatherDescription(lat: Double, lon: Double): String? {
        if (apiKey.isBlank()) return null

        return runCatching {
            val response = api.getCurrentWeather(lat, lon, apiKey)
            val description = response.weather.firstOrNull()?.description
            val temp = response.main.temp
            description?.let { "$it, ${temp.toInt()}°C" }
        }.getOrNull()
    }
}
