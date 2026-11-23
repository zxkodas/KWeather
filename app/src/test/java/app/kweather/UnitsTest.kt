package app.kweather

import app.kweather.ui.demo.DemoData
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.SkyType
import app.kweather.ui.scene.TimeOfDay
import app.kweather.ui.settings.AppSettings
import app.kweather.ui.settings.DistanceUnit
import app.kweather.ui.settings.Fmt
import app.kweather.ui.settings.PrecipUnit
import app.kweather.ui.settings.PressureUnit
import app.kweather.ui.settings.TempUnit
import app.kweather.ui.settings.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class UnitsTest {
    private val metric = Fmt(AppSettings())
    private val imperial = Fmt(
        AppSettings(
            temperature = TempUnit.Fahrenheit,
            wind = WindUnit.Mph,
            pressure = PressureUnit.InHg,
            precipitation = PrecipUnit.Inch,
            distance = DistanceUnit.Mile,
            clock24h = false,
        ),
    )

    @Test fun temperatures() {
        assertEquals("24°", metric.temp(24.4))
        assertEquals("32°", imperial.temp(0.0))
        assertEquals("-18°", imperial.temp(-28.0))
        assertEquals("212°", imperial.temp(100.0))
        // A 15 °C difference is 27 °F, not 59.
        assertEquals("27°", imperial.tempDelta(15.0))
    }

    @Test fun wind() {
        assertEquals("22 km/h", metric.wind(22.0))
        assertEquals("14 mph", imperial.wind(22.0))
        assertEquals("6.1 m/s", Fmt(AppSettings(wind = WindUnit.Ms)).wind(22.0))
        assertEquals("19 m/s", Fmt(AppSettings(wind = WindUnit.Ms)).wind(68.0))
        assertEquals("12 kn", Fmt(AppSettings(wind = WindUnit.Knots)).wind(22.0))
    }

    @Test fun pressureRainDistance() {
        assertEquals("1013", metric.pressureValue(1013.25))
        assertEquals("29.92", imperial.pressureValue(1013.25))
        assertEquals("760", Fmt(AppSettings(pressure = PressureUnit.MmHg)).pressureValue(1013.25))
        assertEquals("25.4 mm", metric.precip(25.4))
        assertEquals("1.00 in", imperial.precip(25.4))
        assertEquals("64 km", metric.distance(64_280.0))
        assertEquals("0.8 km", metric.distance(800.0))
        assertEquals("40 mi", imperial.distance(64_280.0))
    }

    @Test fun clocks() {
        val t = LocalDateTime.of(2026, 11, 22, 15, 5)
        assertEquals("15:05", metric.time(t))
        assertEquals("3:05 PM", imperial.time(t))
        assertEquals("15", metric.hour(t))
        assertEquals("3p", imperial.hour(t))
        assertEquals("12a", imperial.hour(t.withHour(0)))
        assertEquals("7 PM", imperial.hourPhrase(t.withHour(19)))
        assertEquals("Tue 03:00", metric.dayTime(OffsetDateTime.parse("2026-11-24T03:00:00-03:00")))
    }

    @Test fun demoGalleryCoversEverySceneAndTimeOfDay() {
        val cities = DemoData.cities(LocalDate.of(2026, 11, 22), OffsetDateTime.parse("2026-11-22T21:00:00-03:00"))
        val specs = cities.map { SceneSpec.from(it.page.weather!!) }
        val skies = specs.map { it.sky }.toSet()
        val times = specs.map { it.time }.toSet()
        assertEquals(SkyType.entries.toSet(), skies)
        assertEquals(TimeOfDay.entries.toSet(), times)
        assertTrue(cities.any { c -> c.page.alerts.any { it.level.name == "Red" } })
        assertTrue(cities.all { it.page.weather!!.hours.size == 48 && it.page.weather!!.days.size == 10 })
    }
}
