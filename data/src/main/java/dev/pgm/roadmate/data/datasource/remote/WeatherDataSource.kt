package dev.pgm.roadmate.data.datasource.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.pgm.roadmate.data.di.OpenWeatherApiKey
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
internal data class WeatherResponse(
    val weather: List<WeatherDescription>,
    val main: WeatherMain,
    val wind: WeatherWind? = null,
)

@JsonClass(generateAdapter = true)
internal data class WeatherDescription(val description: String)

@JsonClass(generateAdapter = true)
internal data class WeatherMain(
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double? = null,
)

@JsonClass(generateAdapter = true)
internal data class WeatherWind(val speed: Double? = null)

private interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "es"
    ): WeatherResponse

    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByName(
        @Query("q") query: String,
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
        if (apiKey.isBlank()) {
            dev.pgm.roadmate.ml.DebugTrace.log("weather: no API key in this build")
            return null
        }

        return runCatching {
            val response = api.getCurrentWeather(lat, lon, apiKey)
            val description = response.weather.firstOrNull()?.description
            description?.let { desc -> describe(desc, response) }
        }.onFailure {
            dev.pgm.roadmate.ml.DebugTrace.log(
                "weather: fetch failed at $lat,$lon: ${it.javaClass.simpleName}: ${it.message}",
            )
        }.onSuccess {
            dev.pgm.roadmate.ml.DebugTrace.log("weather: $lat,$lon -> ${it ?: "null (empty body)"}")
        }.getOrNull()
    }

    /**
     * Weather for a named place — OpenWeather resolves the name itself, so no
     * downloaded map is needed. Tries "<name>,ES" first (the driver speaks
     * Spanish, most asks are local towns) then the bare name worldwide.
     */
    suspend fun getCurrentWeatherDescriptionFor(placeName: String): String? {
        if (apiKey.isBlank()) {
            dev.pgm.roadmate.ml.DebugTrace.log("weather: no API key in this build")
            return null
        }
        for (q in listOf("$placeName,ES", placeName)) {
            val response = runCatching { api.getCurrentWeatherByName(q, apiKey) }
                .onFailure {
                    dev.pgm.roadmate.ml.DebugTrace.log(
                        "weather: '$q' failed: ${it.javaClass.simpleName}: ${it.message}",
                    )
                }
                .getOrNull() ?: continue
            val sky = response.weather.firstOrNull()?.description ?: continue
            val out = describe(sky, response)
            dev.pgm.roadmate.ml.DebugTrace.log("weather: '$q' -> $out")
            return out
        }
        return null
    }

    /**
     * Short spoken-weather line. Beyond "sky, temperature" it adds the
     * "sensación" (feels-like) when it's noticeably off the real temp and a
     * wind note when it's blowing hard — so two readings minutes apart aren't
     * always the byte-identical sentence, and the extra detail is the kind a
     * driver actually wants.
     */
    private fun describe(sky: String, r: WeatherResponse): String {
        val temp = r.main.temp.roundToInt()
        val sb = StringBuilder("$sky, $temp°C")
        r.main.feelsLike?.roundToInt()?.let { feels ->
            if (abs(feels - temp) >= 3) sb.append(", sensación $feels°C")
        }
        r.wind?.speed?.let { ms ->
            when {
                ms >= 13.8 -> sb.append(", con viento muy fuerte")
                ms >= 8.0 -> sb.append(", con viento fuerte")
            }
        }
        return sb.toString()
    }
}
