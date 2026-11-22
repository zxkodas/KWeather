package app.kweather.data.openmeteo

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

/** Thin client for Open-Meteo. Returns raw JSON so the repository can cache it as-is. */
class OpenMeteoApi(private val http: HttpClient) {

    suspend fun forecastJson(latitude: Double, longitude: Double): String =
        http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("timezone", "auto")
            parameter("forecast_days", 16)
            parameter("forecast_hours", 48)
            parameter("current", CURRENT)
            parameter("hourly", HOURLY)
            parameter("daily", DAILY)
        }.bodyAsText()

    suspend fun airQualityJson(latitude: Double, longitude: Double): String =
        http.get("https://air-quality-api.open-meteo.com/v1/air-quality") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("timezone", "auto")
            parameter("current", "us_aqi,pm2_5,pm10")
        }.bodyAsText()

    private companion object {
        const val CURRENT = "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
            "is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m," +
            "wind_direction_10m,wind_gusts_10m,visibility,uv_index"
        const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
            "weather_code,is_day,wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m," +
            "wind_speed_120m,pressure_msl,uv_index"
        const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
            "wind_direction_10m_dominant,sunrise,sunset,daylight_duration,uv_index_max"
    }
}
