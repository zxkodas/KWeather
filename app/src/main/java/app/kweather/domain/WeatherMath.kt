package app.kweather.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDateTime
import kotlin.math.pow

object WindMath {
    private val points = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun compassPoint(degrees: Double): String {
        val normalized = ((degrees % 360) + 360) % 360
        return points[((normalized + 11.25) / 22.5).toInt() % 16]
    }

    /** Beaufort number from km/h. */
    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        return limits.indexOfFirst { kmh < it }.let { if (it == -1) 12 else it }
    }

    fun beaufortLabel(force: Int): String = when (force) {
        0 -> "Calm"
        1 -> "Light air"
        2 -> "Light breeze"
        3 -> "Gentle breeze"
        4 -> "Moderate breeze"
        5 -> "Fresh breeze"
        6 -> "Strong breeze"
        7 -> "Near gale"
        8 -> "Gale"
        9 -> "Strong gale"
        10 -> "Storm"
        11 -> "Violent storm"
        else -> "Hurricane force"
    }
}

object ComfortMath {
    /** Environment Canada / NWS wind chill, valid at <= 10 °C and wind > 4.8 km/h. */
    fun windChill(tempC: Double, windKmh: Double): Double? {
        if (tempC > 10 || windKmh <= 4.8) return null
        val v = windKmh.pow(0.16)
        return 13.12 + 0.6215 * tempC - 11.37 * v + 0.3965 * tempC * v
    }

    /** Minutes until exposed skin can freeze, per Environment Canada's table (approximate). */
    fun frostbiteMinutes(windChill: Double): Int? = when {
        windChill <= -55 -> 2
        windChill <= -48 -> 5
        windChill <= -40 -> 10
        windChill <= -28 -> 30
        else -> null
    }

    /** NWS heat index (Rothfusz), valid around >= 27 °C and humidity >= 40 %. */
    fun heatIndex(tempC: Double, humidity: Double): Double? {
        if (tempC < 27 || humidity < 40) return null
        val t = tempC * 9 / 5 + 32
        val r = humidity
        val hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r -
            6.83783e-3 * t * t - 5.481717e-2 * r * r + 1.22874e-3 * t * t * r +
            8.5282e-4 * t * r * r - 1.99e-6 * t * t * r * r
        return (hi - 32) * 5 / 9
    }

    fun uvLabel(uv: Double): String = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }

    fun aqiLabel(aqi: Int): String = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for some"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }
}

object MoonMath {
    private const val SYNODIC_DAYS = 29.530588853
    // A known new moon: 2000-01-06 18:14 UTC.
    private val epoch = LocalDateTime.of(2000, 1, 6, 18, 14).toInstant(ZoneOffset.UTC)

    /** 0 = new, 0.5 = full, approaching 1 = new again. */
    fun phase(at: Instant): Double {
        val days = (at.epochSecond - epoch.epochSecond) / 86_400.0
        return ((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS / SYNODIC_DAYS
    }

    /** Fraction of the disc that is lit, 0..1. */
    fun illumination(phase: Double): Double = (1 - kotlin.math.cos(2 * Math.PI * phase)) / 2

    fun name(phase: Double): String = when {
        phase < 0.03 || phase >= 0.97 -> "New moon"
        phase < 0.22 -> "Waxing crescent"
        phase < 0.28 -> "First quarter"
        phase < 0.47 -> "Waxing gibbous"
        phase < 0.53 -> "Full moon"
        phase < 0.72 -> "Waning gibbous"
        phase < 0.78 -> "Last quarter"
        else -> "Waning crescent"
    }

    /** Days until the next full moon. */
    fun daysToFull(phase: Double): Int {
        val delta = if (phase <= 0.5) 0.5 - phase else 1.5 - phase
        return (delta * SYNODIC_DAYS).toInt()
    }
}
