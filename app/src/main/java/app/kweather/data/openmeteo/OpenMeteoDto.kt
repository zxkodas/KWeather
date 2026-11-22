package app.kweather.data.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "GMT",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Double,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    @SerialName("is_day") val isDay: Int,
    @SerialName("precipitation") val precipitation: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("cloud_cover") val cloudCover: Double = 0.0,
    @SerialName("pressure_msl") val pressure: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Double,
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("visibility") val visibility: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
)

@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
    @SerialName("precipitation") val precipitation: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("is_day") val isDay: List<Int?>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>,
    @SerialName("wind_direction_10m") val windDirection: List<Double?>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double?>,
    @SerialName("wind_speed_80m") val windSpeed80m: List<Double?>,
    @SerialName("wind_speed_120m") val windSpeed120m: List<Double?>,
    @SerialName("pressure_msl") val pressure: List<Double?>,
    @SerialName("uv_index") val uvIndex: List<Double?>,
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>,
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?>,
    @SerialName("wind_gusts_10m_max") val windGustsMax: List<Double?>,
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Double?>,
    @SerialName("sunrise") val sunrise: List<String?>,
    @SerialName("sunset") val sunset: List<String?>,
    @SerialName("daylight_duration") val daylightDuration: List<Double?>,
    @SerialName("uv_index_max") val uvIndexMax: List<Double?>,
)

@Serializable
data class AirQualityResponse(
    val current: AirQualityCurrentDto? = null,
)

@Serializable
data class AirQualityCurrentDto(
    @SerialName("us_aqi") val usAqi: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("pm10") val pm10: Double? = null,
)
