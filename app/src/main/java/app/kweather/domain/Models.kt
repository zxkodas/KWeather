package app.kweather.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
)

data class Weather(
    val place: Place,
    val fetchedAt: LocalDateTime,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val airQuality: AirQuality?,
) {
    val today: Day? get() = days.firstOrNull()
}

data class Wind(
    /** km/h */
    val speed: Double,
    /** Degrees the wind comes FROM (meteorological convention). */
    val direction: Double,
    /** km/h */
    val gusts: Double,
)

data class Current(
    val time: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Double,
    val dewPoint: Double?,
    val isDay: Boolean,
    val precipitation: Double,
    val condition: Condition,
    val cloudCover: Double,
    val pressure: Double,
    val wind: Wind,
    /** Metres. */
    val visibility: Double?,
    val uvIndex: Double?,
)

data class Hour(
    val time: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val precipitationProbability: Int,
    val precipitation: Double,
    val condition: Condition,
    val isDay: Boolean,
    val wind: Wind,
    val windSpeed80m: Double?,
    val windSpeed120m: Double?,
    val pressure: Double?,
    val uvIndex: Double?,
)

data class Day(
    val date: LocalDate,
    val condition: Condition,
    val max: Double,
    val min: Double,
    val precipitationSum: Double,
    val precipitationProbability: Int,
    val windMax: Double,
    val gustMax: Double,
    val windDirection: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    /** Seconds. */
    val daylight: Double,
    val uvMax: Double?,
)

data class AirQuality(
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
)
