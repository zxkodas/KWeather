package app.kweather.ui.demo

import app.kweather.domain.AirQuality
import app.kweather.domain.Alert
import app.kweather.domain.AlertLevel
import app.kweather.domain.Condition
import app.kweather.domain.Current
import app.kweather.domain.Day
import app.kweather.domain.Hour
import app.kweather.domain.MoonMath
import app.kweather.domain.Place
import app.kweather.domain.Weather
import app.kweather.domain.Wind
import app.kweather.ui.home.PageState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

data class DemoCity(val title: String, val description: String, val page: PageState)

/** Made-up but realistic cities, one per scene or feature, for checking designs without waiting for the weather. */
object DemoData {

    const val SOURCE = "Demo"

    private data class Spec(
        val title: String,
        val description: String,
        val name: String,
        val region: String,
        val lat: Double,
        val lon: Double,
        val date: LocalDate,
        val time: LocalTime,
        val condition: Condition,
        val temp: Double,
        val feels: Double = temp,
        val humidity: Double = 60.0,
        val wind: Double = 10.0,
        val windDir: Double = 90.0,
        val gust: Double = wind * 1.6,
        val precip: Double = 0.0,
        val rainChance: Int = 0,
        val pressure: Double = 1013.0,
        val visibility: Double = 24_000.0,
        /** Percent; null = typical for the condition. Partly cloudy skies draw more clouds as it rises. */
        val cloudCover: Double? = null,
        val uv: Double = 0.0,
        val sunrise: LocalTime? = LocalTime.of(5, 50),
        val sunset: LocalTime? = LocalTime.of(20, 0),
        /** Seconds of daylight; null = from sunrise and sunset. */
        val daylight: Double? = null,
        val later: List<Condition> = listOf(condition),
        val tempTrend: Double = -0.4,
        val aqi: Int = 28,
        val alerts: (OffsetDateTime) -> List<Alert> = { emptyList() },
    )

    fun cities(today: LocalDate = LocalDate.now(), now: OffsetDateTime = OffsetDateTime.now()): List<DemoCity> {
        val fullMoon = dateWithPhase(today, 0.5)
        val crescent = dateWithPhase(today, 0.15)
        val specs = listOf(
            Spec(
                "Sunny afternoon", "Clear day, high UV, light breeze",
                "Buenos Aires", "Argentina", -34.60, -58.38, today, LocalTime.of(15, 0),
                Condition.Clear, 28.0, feels = 29.0, humidity = 40.0, wind = 12.0, windDir = 45.0, uv = 8.0,
                later = listOf(Condition.Clear, Condition.MostlyClear, Condition.PartlyCloudy),
            ),
            Spec(
                "Partly cloudy dawn", "Low sun and scattered clouds",
                "Córdoba", "Argentina", -31.42, -64.18, today.plusDays(1), LocalTime.of(6, 10),
                Condition.PartlyCloudy, 15.0, wind = 8.0, windDir = 20.0, sunrise = LocalTime.of(6, 0), tempTrend = 1.2,
                cloudCover = 30.0,
            ),
            Spec(
                "Overcast dusk", "Grey sky as the sun sets",
                "Ushuaia", "Tierra del Fuego, Argentina", -54.80, -68.30, today, LocalTime.of(21, 45),
                Condition.Overcast, 8.0, feels = 5.0, wind = 22.0, windDir = 250.0, sunrise = LocalTime.of(5, 10),
                sunset = LocalTime.of(21, 50), pressure = 1004.0,
            ),
            Spec(
                "Morning fog", "Visibility down to a few hundred metres",
                "Mar del Plata", "Buenos Aires, Argentina", -38.00, -57.55, today, LocalTime.of(8, 0),
                Condition.Fog, 12.0, humidity = 98.0, wind = 4.0, visibility = 300.0,
                later = listOf(Condition.Fog, Condition.Overcast, Condition.PartlyCloudy), tempTrend = 0.8,
            ),
            Spec(
                "Drizzle", "Light, steady drizzle",
                "Bahía Blanca", "Buenos Aires, Argentina", -38.72, -62.27, today, LocalTime.of(11, 0),
                Condition.Drizzle, 14.0, humidity = 90.0, wind = 14.0, windDir = 160.0, precip = 0.3, rainChance = 70,
            ),
            Spec(
                "Heavy rain at night", "Dense rain, gusty wind and a yellow alert",
                "Posadas", "Misiones, Argentina", -27.37, -55.90, today, LocalTime.of(23, 0),
                Condition.HeavyRain, 21.0, humidity = 96.0, wind = 30.0, windDir = 140.0, gust = 55.0, precip = 12.0,
                rainChance = 100, pressure = 1002.0, visibility = 3_000.0,
                later = listOf(Condition.HeavyRain, Condition.Rain, Condition.Showers),
                alerts = { n -> listOf(alert("rain", "Lluvias", AlertLevel.Yellow, n.minusHours(2), n.plusHours(10), shortTerm = false)) },
            ),
            Spec(
                "Thunderstorm with alerts", "Lightning, hail risk, orange and yellow alerts",
                "Rosario", "Santa Fe, Argentina", -32.95, -60.64, today, LocalTime.of(18, 0),
                Condition.Thunderstorm, 23.0, feels = 24.0, humidity = 85.0, wind = 38.0, windDir = 157.5, gust = 75.0,
                precip = 8.0, rainChance = 90, pressure = 1004.0,
                later = listOf(Condition.Thunderstorm, Condition.Showers, Condition.PartlyCloudy),
                alerts = { n ->
                    listOf(
                        alert("acp", "TORMENTAS FUERTES", AlertLevel.Orange, n.minusMinutes(30), n.plusHours(2), shortTerm = true),
                        alert("storms", "Tormentas", AlertLevel.Yellow, n.minusHours(3), n.plusHours(9), shortTerm = false),
                        alert("wind", "Viento", AlertLevel.Yellow, n.plusDays(1), n.plusDays(1).plusHours(12), shortTerm = false),
                    )
                },
            ),
            Spec(
                "Snowfall", "Steady snow around freezing",
                "Bariloche", "Río Negro, Argentina", -41.13, -71.31, today, LocalTime.of(13, 0),
                Condition.Snow, -1.0, feels = -6.0, humidity = 92.0, wind = 15.0, windDir = 270.0, precip = 1.5,
                rainChance = 85, visibility = 2_000.0, later = listOf(Condition.Snow, Condition.HeavySnow, Condition.SnowShowers),
            ),
            Spec(
                "Blowing snow, polar night", "Wind chill, frostbite risk, a red alert and no sun",
                "Alert", "Nunavut, Canada", 82.50, -62.35, today, LocalTime.of(13, 0),
                Condition.Snow, -28.0, feels = -43.0, humidity = 76.0, wind = 35.0, windDir = 337.5, gust = 52.0,
                precip = 0.4, rainChance = 60, pressure = 1030.6, visibility = 800.0, sunrise = null, sunset = null,
                daylight = 0.0, tempTrend = -0.1,
                alerts = { n -> listOf(alert("cold", "Frío extremo", AlertLevel.Red, n.minusHours(5), n.plusDays(1), shortTerm = false)) },
            ),
            Spec(
                "Heat wave", "Heat index above the air temperature and a red alert",
                "Santiago del Estero", "Argentina", -27.78, -64.26, today, LocalTime.of(16, 0),
                Condition.Clear, 40.0, feels = 44.0, humidity = 45.0, wind = 9.0, windDir = 10.0, uv = 11.0, aqi = 72,
                alerts = { n -> listOf(alert("heat", "Calor extremo", AlertLevel.Red, n.minusHours(6), n.plusDays(2), shortTerm = false)) },
            ),
            Spec(
                "Clear night, full moon", "Stars, a full moon and calm air",
                "Salta", "Argentina", -24.79, -65.41, fullMoon, LocalTime.of(23, 0),
                Condition.Clear, 18.0, wind = 5.0, windDir = 200.0, sunrise = LocalTime.of(6, 20), sunset = LocalTime.of(19, 50),
            ),
            Spec(
                "Crescent moon, some clouds", "Partly cloudy night with a thin moon",
                "Mendoza", "Argentina", -32.89, -68.83, crescent, LocalTime.of(21, 30),
                Condition.PartlyCloudy, 20.0, wind = 7.0, windDir = 180.0, sunrise = LocalTime.of(6, 5), sunset = LocalTime.of(20, 30),
                cloudCover = 70.0,
            ),
            Spec(
                "Gale-force wind", "Beaufort 8 with gusts near 100 km/h and an orange alert",
                "Comodoro Rivadavia", "Chubut, Argentina", -45.86, -67.48, today, LocalTime.of(12, 0),
                Condition.PartlyCloudy, 17.0, feels = 11.0, humidity = 30.0, wind = 65.0, windDir = 260.0, gust = 98.0,
                pressure = 998.0, aqi = 55,
                alerts = { n -> listOf(alert("gale", "Viento", AlertLevel.Orange, n.minusHours(4), n.plusHours(14), shortTerm = false)) },
            ),
            Spec(
                "Midnight sun", "Daylight around the clock",
                "Tromsø", "Troms, Norway", 69.65, 18.96, today, LocalTime.of(1, 0),
                Condition.MostlyClear, 12.0, wind = 6.0, windDir = 300.0, uv = 1.0, sunrise = null, sunset = null,
                daylight = 86_400.0, tempTrend = 0.3,
            ),
        )
        return specs.mapIndexed { i, s -> DemoCity(s.title, s.description, page(i, s, now)) }
    }

    private fun page(index: Int, s: Spec, now: OffsetDateTime): PageState {
        val place = Place("demo:$index", s.name, s.region, s.lat, s.lon, null)
        val at = LocalDateTime.of(s.date, s.time)
        val sunrise = s.sunrise?.let { LocalDateTime.of(s.date, it) }
        val sunset = s.sunset?.let { LocalDateTime.of(s.date, it) }
        val daylight = s.daylight ?: if (sunrise != null && sunset != null) Duration.between(sunrise, sunset).seconds.toDouble() else 0.0
        fun isDay(t: LocalDateTime): Boolean = when {
            daylight >= 86_000 -> true
            daylight <= 0 -> false
            else -> t.toLocalTime().let { it >= (s.sunrise ?: LocalTime.MIN) && it < (s.sunset ?: LocalTime.MAX) }
        }

        val hours = (0 until 48).map { i ->
            val t = at.withMinute(0).plusHours(i.toLong())
            val swing = 4 * (sin(2 * PI * (t.hour - 9) / 24) - sin(2 * PI * (at.hour - 9) / 24))
            val speed = max(0.0, s.wind * (1 + 0.3 * sin(i / 3.0)) - i * 0.15)
            Hour(
                time = t,
                temperature = s.temp + swing + s.tempTrend * i * 0.2,
                feelsLike = s.feels + swing,
                precipitationProbability = max(0, s.rainChance - i * 6),
                precipitation = if (i < 6) s.precip * (1 - i / 6.0) else 0.0,
                condition = s.later[(i / 6) % s.later.size],
                isDay = isDay(t),
                wind = Wind(speed, s.windDir + 4 * i, speed * (s.gust / s.wind.coerceAtLeast(1.0))),
                windSpeed80m = speed * 1.35,
                windSpeed120m = speed * 1.5,
                pressure = s.pressure + 0.3 * i * (if (s.precip > 0) 1 else -1),
                uvIndex = if (isDay(t)) s.uv * abs(sin(PI * (t.hour - 6) / 14)) else 0.0,
            )
        }

        val days = (0 until 10).map { d ->
            val date = s.date.plusDays(d.toLong())
            val wobble = 3 * sin(d * 1.3)
            val cond = if (d == 0) s.condition else s.later.getOrElse(d % (s.later.size + 1)) { Condition.PartlyCloudy }
            Day(
                date = date,
                condition = cond,
                max = s.temp + 3 + wobble,
                min = s.temp - 7 + wobble / 2,
                precipitationSum = if (cond.isWet || cond.isSnowy) 4.0 + d else 0.0,
                precipitationProbability = if (cond.isWet || cond.isSnowy) 60 + (d * 7) % 35 else (d * 13) % 20,
                windMax = s.wind * (0.8 + 0.1 * (d % 4)),
                gustMax = s.gust * (0.75 + 0.1 * (d % 5)),
                windDirection = s.windDir,
                sunrise = sunrise?.plusDays(d.toLong()),
                sunset = sunset?.plusDays(d.toLong()),
                daylight = daylight,
                uvMax = s.uv,
            )
        }

        val current = Current(
            time = at,
            temperature = s.temp,
            feelsLike = s.feels,
            humidity = s.humidity,
            dewPoint = s.temp - (100 - s.humidity) / 5,
            isDay = isDay(at),
            precipitation = s.precip,
            condition = s.condition,
            cloudCover = s.cloudCover ?: when (s.condition) {
                Condition.Clear -> 5.0
                Condition.MostlyClear -> 15.0
                Condition.PartlyCloudy -> 45.0
                else -> 95.0
            },
            pressure = s.pressure,
            wind = Wind(s.wind, s.windDir, s.gust),
            visibility = s.visibility,
            uvIndex = s.uv,
        )
        val weather = Weather(place, at, current, hours, days, AirQuality(s.aqi, null, null))
        return PageState(
            key = place.id,
            place = place,
            isCurrent = false,
            weather = weather,
            refreshing = false,
            error = null,
            locationIssue = null,
            alerts = s.alerts(now),
        )
    }

    private fun alert(
        id: String,
        event: String,
        level: AlertLevel,
        onset: OffsetDateTime,
        expires: OffsetDateTime,
        shortTerm: Boolean,
    ) = Alert(
        id = "demo-$id",
        source = SOURCE,
        sourceName = "KWeather demo data",
        event = event,
        headline = if (shortTerm) "AVISO ${levelWord(level)} POR $event" else event,
        description = "Example alert text. A real alert shows the issuing service's official description of what to expect here.",
        instruction = "1- This is an example. 2- Real alerts list the official safety steps here. 3- Nothing on this page is a real warning.",
        level = level,
        urgency = if (shortTerm) "Immediate" else "Expected",
        sent = onset,
        onset = if (shortTerm) null else onset,
        expires = expires,
        web = null,
        areaDescription = if (shortTerm) "Example area" else "",
        polygons = emptyList(),
    )

    private fun levelWord(level: AlertLevel) = when (level) {
        AlertLevel.Yellow -> "AMARILLO"
        AlertLevel.Orange -> "NARANJA"
        AlertLevel.Red -> "ROJO"
    }

    /** The nearest date within a month whose moon phase is closest to [target]. */
    private fun dateWithPhase(from: LocalDate, target: Double): LocalDate =
        (-15..15).map { from.plusDays(it.toLong()) }.minBy { d ->
            val phase = MoonMath.phase(LocalDateTime.of(d, LocalTime.of(23, 0)).toInstant(ZoneOffset.UTC))
            abs(phase - target)
        }
}
