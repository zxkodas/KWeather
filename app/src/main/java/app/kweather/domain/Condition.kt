package app.kweather.domain

/** Broad weather condition, derived from the WMO weather code Open-Meteo returns. */
enum class Condition(val label: String) {
    Clear("Clear"),
    MostlyClear("Mostly clear"),
    PartlyCloudy("Partly cloudy"),
    Overcast("Overcast"),
    Fog("Fog"),
    Drizzle("Drizzle"),
    Rain("Rain"),
    HeavyRain("Heavy rain"),
    FreezingRain("Freezing rain"),
    Snow("Snow"),
    HeavySnow("Heavy snow"),
    Showers("Showers"),
    SnowShowers("Snow showers"),
    Thunderstorm("Thunderstorm"),
    Hail("Thunderstorm with hail");

    val isWet: Boolean
        get() = this in setOf(Drizzle, Rain, HeavyRain, FreezingRain, Showers, Thunderstorm, Hail)

    val isSnowy: Boolean
        get() = this in setOf(Snow, HeavySnow, SnowShowers)

    companion object {
        /** WMO 4677 subset used by Open-Meteo. */
        fun fromWmo(code: Int): Condition = when (code) {
            0 -> Clear
            1 -> MostlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45, 48 -> Fog
            51, 53, 55 -> Drizzle
            56, 57, 66, 67 -> FreezingRain
            61, 63 -> Rain
            65 -> HeavyRain
            71, 73, 77 -> Snow
            75 -> HeavySnow
            80, 81, 82 -> Showers
            85, 86 -> SnowShowers
            95 -> Thunderstorm
            96, 99 -> Hail
            else -> PartlyCloudy
        }
    }
}

fun Condition.labelFor(isDay: Boolean): String = when {
    !isDay && this == Condition.Clear -> "Clear night"
    !isDay && this == Condition.MostlyClear -> "Mostly clear"
    isDay && this == Condition.Clear -> "Sunny"
    isDay && this == Condition.MostlyClear -> "Mostly sunny"
    else -> label
}
