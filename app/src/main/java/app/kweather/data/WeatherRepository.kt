package app.kweather.data

import app.kweather.data.openmeteo.AirQualityResponse
import app.kweather.data.openmeteo.ForecastResponse
import app.kweather.data.openmeteo.OpenMeteoApi
import app.kweather.data.openmeteo.toWeather
import app.kweather.domain.Place
import app.kweather.domain.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

/**
 * Fetches forecasts and keeps the last good response per place on disk,
 * so the app opens instantly and still shows something offline.
 */
class WeatherRepository(
    private val api: OpenMeteoApi,
    private val cacheDir: File,
    private val json: Json,
) {
    @Serializable
    private data class CacheEntry(
        val fetchedAt: String,
        val forecast: String,
        val air: String? = null,
    )

    suspend fun cached(place: Place): Weather? = withContext(Dispatchers.IO) {
        val file = cacheFile(place)
        if (!file.exists()) return@withContext null
        runCatching {
            val entry = json.decodeFromString<CacheEntry>(file.readText())
            decode(place, entry)
        }.getOrNull()
    }

    suspend fun refresh(place: Place): Weather = coroutineScope {
        val forecast = async { api.forecastJson(place.latitude, place.longitude) }
        // Air quality is a nice-to-have: never let it fail the whole refresh.
        val air = async { runCatching { api.airQualityJson(place.latitude, place.longitude) }.getOrNull() }
        val entry = CacheEntry(
            fetchedAt = LocalDateTime.now().toString(),
            forecast = forecast.await(),
            air = air.await(),
        )
        val weather = decode(place, entry)
        withContext(Dispatchers.IO) {
            cacheFile(place).writeText(json.encodeToString(CacheEntry.serializer(), entry))
        }
        weather
    }

    private fun decode(place: Place, entry: CacheEntry): Weather {
        val forecast = json.decodeFromString<ForecastResponse>(entry.forecast)
        val air = entry.air?.let { runCatching { json.decodeFromString<AirQualityResponse>(it) }.getOrNull() }
        return forecast.toWeather(place, LocalDateTime.parse(entry.fetchedAt), air)
    }

    private fun cacheFile(place: Place): File {
        val key = "%.3f_%.3f".format(java.util.Locale.ROOT, place.latitude, place.longitude)
        return File(cacheDir, "forecast_$key.json")
    }
}
