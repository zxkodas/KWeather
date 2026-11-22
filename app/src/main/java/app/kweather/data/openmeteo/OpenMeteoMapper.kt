package app.kweather.data.openmeteo

import app.kweather.domain.AirQuality
import app.kweather.domain.Condition
import app.kweather.domain.Current
import app.kweather.domain.Day
import app.kweather.domain.Hour
import app.kweather.domain.Place
import app.kweather.domain.Weather
import app.kweather.domain.Wind
import java.time.LocalDate
import java.time.LocalDateTime

/** Open-Meteo returns local ISO times without an offset because we ask for timezone=auto. */
private fun time(s: String): LocalDateTime = LocalDateTime.parse(s)

fun ForecastResponse.toWeather(place: Place, fetchedAt: LocalDateTime, air: AirQualityResponse?): Weather {
    val c = current
    val currentDomain = Current(
        time = time(c.time),
        temperature = c.temperature,
        feelsLike = c.apparentTemperature,
        humidity = c.humidity,
        dewPoint = c.dewPoint,
        isDay = c.isDay == 1,
        precipitation = c.precipitation,
        condition = Condition.fromWmo(c.weatherCode),
        cloudCover = c.cloudCover,
        pressure = c.pressure,
        wind = Wind(c.windSpeed, c.windDirection, c.windGusts),
        visibility = c.visibility,
        uvIndex = c.uvIndex,
    )

    val h = hourly
    val hours = h.time.indices.mapNotNull { i ->
        val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
        Hour(
            time = time(h.time[i]),
            temperature = temp,
            feelsLike = h.apparentTemperature.getOrNull(i) ?: temp,
            precipitationProbability = h.precipitationProbability.getOrNull(i) ?: 0,
            precipitation = h.precipitation.getOrNull(i) ?: 0.0,
            condition = Condition.fromWmo(h.weatherCode.getOrNull(i) ?: 2),
            isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
            wind = Wind(
                speed = h.windSpeed.getOrNull(i) ?: 0.0,
                direction = h.windDirection.getOrNull(i) ?: 0.0,
                gusts = h.windGusts.getOrNull(i) ?: 0.0,
            ),
            windSpeed80m = h.windSpeed80m.getOrNull(i),
            windSpeed120m = h.windSpeed120m.getOrNull(i),
            pressure = h.pressure.getOrNull(i),
            uvIndex = h.uvIndex.getOrNull(i),
        )
    }

    val d = daily
    val days = d.time.indices.mapNotNull { i ->
        val max = d.temperatureMax.getOrNull(i) ?: return@mapNotNull null
        val min = d.temperatureMin.getOrNull(i) ?: return@mapNotNull null
        Day(
            date = LocalDate.parse(d.time[i]),
            condition = Condition.fromWmo(d.weatherCode.getOrNull(i) ?: 2),
            max = max,
            min = min,
            precipitationSum = d.precipitationSum.getOrNull(i) ?: 0.0,
            precipitationProbability = d.precipitationProbabilityMax.getOrNull(i) ?: 0,
            windMax = d.windSpeedMax.getOrNull(i) ?: 0.0,
            gustMax = d.windGustsMax.getOrNull(i) ?: 0.0,
            windDirection = d.windDirectionDominant.getOrNull(i) ?: 0.0,
            sunrise = d.sunrise.getOrNull(i)?.let(::time),
            sunset = d.sunset.getOrNull(i)?.let(::time),
            daylight = d.daylightDuration.getOrNull(i) ?: 0.0,
            uvMax = d.uvIndexMax.getOrNull(i),
        )
    }

    return Weather(
        place = place,
        fetchedAt = fetchedAt,
        current = currentDomain,
        hours = hours,
        days = days,
        airQuality = air?.current?.let {
            AirQuality(usAqi = it.usAqi?.toInt(), pm25 = it.pm25, pm10 = it.pm10)
        },
    )
}
