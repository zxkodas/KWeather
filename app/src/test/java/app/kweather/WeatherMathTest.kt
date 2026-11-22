package app.kweather

import app.kweather.domain.ComfortMath
import app.kweather.domain.Condition
import app.kweather.domain.MoonMath
import app.kweather.domain.WindMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class WeatherMathTest {

    @Test fun compassPoints() {
        assertEquals("N", WindMath.compassPoint(0.0))
        assertEquals("N", WindMath.compassPoint(359.0))
        assertEquals("SSE", WindMath.compassPoint(157.5))
        assertEquals("NNW", WindMath.compassPoint(337.0))
        assertEquals("W", WindMath.compassPoint(-90.0))
    }

    @Test fun beaufortScale() {
        assertEquals(0, WindMath.beaufort(0.5))
        assertEquals(4, WindMath.beaufort(22.0))
        assertEquals(5, WindMath.beaufort(35.0))
        assertEquals(8, WindMath.beaufort(70.0))
        assertEquals(12, WindMath.beaufort(140.0))
    }

    @Test fun windChillMatchesEnvironmentCanadaTable() {
        // EC table: -30 °C at 30 km/h ≈ -46.
        assertEquals(-46.0, ComfortMath.windChill(-30.0, 30.0)!!, 1.0)
        assertNull(ComfortMath.windChill(15.0, 30.0))
        assertNull(ComfortMath.windChill(-5.0, 3.0))
    }

    @Test fun heatIndexMatchesNwsChart() {
        // NWS chart: 32 °C (90 °F) at 60 % ≈ 100 °F ≈ 38 °C.
        assertEquals(38.0, ComfortMath.heatIndex(32.0, 60.0)!!, 1.5)
        assertNull(ComfortMath.heatIndex(20.0, 80.0))
    }

    @Test fun moonPhaseKnownDates() {
        // Full moon on 2024-04-23 23:49 UTC.
        val full = MoonMath.phase(LocalDateTime.of(2024, 4, 23, 23, 49).toInstant(ZoneOffset.UTC))
        assertEquals(0.5, full, 0.03)
        // New moon on 2024-04-08 18:21 UTC (the eclipse).
        val new = MoonMath.phase(LocalDateTime.of(2024, 4, 8, 18, 21).toInstant(ZoneOffset.UTC))
        assertTrue(new < 0.03 || new > 0.97)
    }

    @Test fun wmoCodes() {
        assertEquals(Condition.Clear, Condition.fromWmo(0))
        assertEquals(Condition.Thunderstorm, Condition.fromWmo(95))
        assertEquals(Condition.HeavySnow, Condition.fromWmo(75))
        assertTrue(Condition.fromWmo(63).isWet)
        assertTrue(Condition.fromWmo(86).isSnowy)
    }
}
