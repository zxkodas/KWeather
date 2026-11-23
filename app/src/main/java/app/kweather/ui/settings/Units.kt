package app.kweather.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class TempUnit(val label: String) { Celsius("°C"), Fahrenheit("°F") }

enum class WindUnit(val label: String, internal val perKmh: Double) {
    Kmh("km/h", 1.0),
    Ms("m/s", 1 / 3.6),
    Mph("mph", 0.621371),
    Knots("kn", 0.539957),
}

enum class PressureUnit(val label: String, internal val perHpa: Double) {
    Hpa("hPa", 1.0),
    InHg("inHg", 0.0295300),
    MmHg("mmHg", 0.750062),
}

enum class PrecipUnit(val label: String) { Mm("mm"), Inch("in") }

enum class DistanceUnit(val label: String) { Km("km"), Mile("mi") }

/** Everything the user can change. Data from the APIs always stays metric; only display converts. */
@Immutable
data class AppSettings(
    val temperature: TempUnit = TempUnit.Celsius,
    val wind: WindUnit = WindUnit.Kmh,
    val pressure: PressureUnit = PressureUnit.Hpa,
    val precipitation: PrecipUnit = PrecipUnit.Mm,
    val distance: DistanceUnit = DistanceUnit.Km,
    val clock24h: Boolean = true,
    val animations: Boolean = true,
)

/** Formats metric values for display in the user's units. */
@Immutable
class Fmt(val settings: AppSettings) {
    private val timePattern = DateTimeFormatter.ofPattern(if (settings.clock24h) "HH:mm" else "h:mm a", Locale.ENGLISH)
    private val dayTimePattern = DateTimeFormatter.ofPattern(if (settings.clock24h) "EEE HH:mm" else "EEE h:mm a", Locale.ENGLISH)

    fun tempValue(celsius: Double): Double =
        if (settings.temperature == TempUnit.Fahrenheit) celsius * 9 / 5 + 32 else celsius

    /** "24°" */
    fun temp(celsius: Double): String = "${tempValue(celsius).roundToInt()}°"

    /** A temperature difference, e.g. how much colder the wind makes it feel. */
    fun tempDelta(celsiusDelta: Double): String {
        val v = if (settings.temperature == TempUnit.Fahrenheit) celsiusDelta * 9 / 5 else celsiusDelta
        return "${v.roundToInt()}°"
    }

    val windUnit: String get() = settings.wind.label

    fun windValue(kmh: Double): String {
        val v = kmh * settings.wind.perKmh
        return if (settings.wind == WindUnit.Ms && v < 10) String.format(Locale.ROOT, "%.1f", v) else v.roundToInt().toString()
    }

    fun wind(kmh: Double): String = "${windValue(kmh)} $windUnit"

    val pressureUnit: String get() = settings.pressure.label

    fun pressureValue(hpa: Double): String {
        val v = hpa * settings.pressure.perHpa
        return if (settings.pressure == PressureUnit.InHg) String.format(Locale.ROOT, "%.2f", v) else v.roundToInt().toString()
    }

    fun precip(mm: Double): String = when (settings.precipitation) {
        PrecipUnit.Mm -> String.format(Locale.ROOT, "%.1f mm", mm)
        PrecipUnit.Inch -> String.format(Locale.ROOT, "%.2f in", mm / 25.4)
    }

    fun distance(meters: Double): String = when (settings.distance) {
        DistanceUnit.Km -> km(meters / 1000, "km")
        DistanceUnit.Mile -> km(meters / 1609.344, "mi")
    }

    private fun km(v: Double, unit: String) =
        if (v >= 10) "${v.roundToInt()} $unit" else String.format(Locale.ROOT, "%.1f %s", v, unit)

    fun time(t: LocalDateTime): String = t.format(timePattern)

    fun dayTime(t: OffsetDateTime): String = t.format(dayTimePattern)

    /** Short hour label for chart axes: "15" or "3p". */
    fun hour(t: LocalDateTime): String = if (settings.clock24h) {
        "%02d".format(t.hour)
    } else {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        "$h${if (t.hour < 12) "a" else "p"}"
    }

    /** "19:00" or "7 PM", for "peak at ..." phrases. */
    fun hourPhrase(t: LocalDateTime): String = if (settings.clock24h) {
        "%02d:00".format(t.hour)
    } else {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        "$h ${if (t.hour < 12) "AM" else "PM"}"
    }
}

val LocalFmt = staticCompositionLocalOf { Fmt(AppSettings()) }
