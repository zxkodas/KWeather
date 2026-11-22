package app.kweather.ui.scene

import app.kweather.domain.Condition
import app.kweather.domain.MoonMath
import app.kweather.domain.Weather
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class SkyType { Clear, PartlyCloudy, Overcast, Fog, Drizzle, Rain, Storm, Snow }

enum class TimeOfDay { Dawn, Day, Dusk, Night }

/** Everything the scene needs to draw itself. Particle behaviour follows the real data. */
data class SceneSpec(
    val sky: SkyType,
    val time: TimeOfDay,
    /** km/h */
    val windSpeed: Double,
    /** Degrees the wind comes from. */
    val windDirection: Double,
    /** mm/h, drives particle density. */
    val precipitation: Double,
    /** 0 = new, 0.5 = full. */
    val moonPhase: Double,
    val heavy: Boolean,
    /** The lit side of a waxing moon is on the left in the southern hemisphere. */
    val southern: Boolean = false,
) {
    companion object {
        fun from(weather: Weather): SceneSpec {
            val c = weather.current
            val sky = when (c.condition) {
                Condition.Clear, Condition.MostlyClear -> SkyType.Clear
                Condition.PartlyCloudy -> SkyType.PartlyCloudy
                Condition.Overcast -> SkyType.Overcast
                Condition.Fog -> SkyType.Fog
                Condition.Drizzle -> SkyType.Drizzle
                Condition.Rain, Condition.HeavyRain, Condition.FreezingRain, Condition.Showers -> SkyType.Rain
                Condition.Thunderstorm, Condition.Hail -> SkyType.Storm
                Condition.Snow, Condition.HeavySnow, Condition.SnowShowers -> SkyType.Snow
            }
            return SceneSpec(
                sky = sky,
                time = timeOfDay(c.time, c.isDay, weather),
                windSpeed = c.wind.speed,
                windDirection = c.wind.direction,
                precipitation = c.precipitation,
                moonPhase = MoonMath.phase(c.time.toInstant(ZoneOffset.UTC)),
                heavy = c.condition in setOf(Condition.HeavyRain, Condition.HeavySnow, Condition.Hail),
                southern = weather.place.latitude < 0,
            )
        }

        private fun timeOfDay(now: LocalDateTime, isDay: Boolean, weather: Weather): TimeOfDay {
            val today = weather.today ?: return if (isDay) TimeOfDay.Day else TimeOfDay.Night
            if (today.daylight <= 0.0) return TimeOfDay.Night // polar night
            if (today.daylight >= 86_000) return TimeOfDay.Day // midnight sun
            val window = Duration.ofMinutes(40)
            today.sunrise?.let { if (Duration.between(it, now).abs() <= window) return TimeOfDay.Dawn }
            today.sunset?.let { if (Duration.between(it, now).abs() <= window) return TimeOfDay.Dusk }
            return if (isDay) TimeOfDay.Day else TimeOfDay.Night
        }
    }
}
