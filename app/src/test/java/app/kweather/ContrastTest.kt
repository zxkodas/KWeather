package app.kweather

import androidx.compose.ui.graphics.Color
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.scene.SkyType
import app.kweather.ui.scene.TimeOfDay
import app.kweather.ui.scene.composite
import app.kweather.ui.scene.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA for every scene palette: 4.5:1 for text, 3:1 for icons and chart marks. */
class ContrastTest {
    private val scenes = SkyType.entries.flatMap { sky -> TimeOfDay.entries.map { sky to it } }

    private fun palette(sky: SkyType, time: TimeOfDay) =
        ScenePalette.forSpec(SceneSpec(sky, time, 10.0, 90.0, 0.0, 0.25, heavy = false))

    private fun check(failures: MutableList<String>, scene: String, what: String, a: Color, b: Color, min: Double) {
        val ratio = contrast(a, b)
        if (ratio < min) failures += "$scene $what: ${"%.2f".format(ratio)} < $min"
    }

    @Test fun formulaMatchesKnownValues() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(4.48, contrast(Color(0xFF777777), Color.White), 0.01)
        assertEquals(2.82, contrast(Color(0xFF555555), Color.Black), 0.01)
    }

    @Test fun heroTextReadsOverTheWholeSky() {
        val failures = mutableListOf<String>()
        for ((sky, time) in scenes) {
            val spec = SceneSpec(sky, time, 10.0, 90.0, 0.0, 0.25, heavy = false)
            val p = ScenePalette.forSpec(spec)
            ScenePalette.heroBackdrops(spec, p.skyTop, p.skyBottom, p.cloud, p.cloudShade).forEach { backdrop ->
                check(failures, "$sky/$time", "hero", p.heroInk, composite(backdrop, p.heroScrim), 4.5)
            }
        }
        assertTrue(failures.distinct().joinToString("\n"), failures.isEmpty())
    }

    @Test fun cardTextAndMarks() {
        val failures = mutableListOf<String>()
        for ((sky, time) in scenes) {
            val p = palette(sky, time)
            val s = "$sky/$time"
            check(failures, s, "onCard/card", p.onCard, p.card, 4.5)
            check(failures, s, "onCardVariant/card", p.onCardVariant, p.card, 4.5)
            check(failures, s, "onCardVariant/background", p.onCardVariant, p.background, 4.5)
            check(failures, s, "gust/card", p.gust, p.card, 4.5)
            check(failures, s, "rain/card", p.rain, p.card, 4.5)
            check(failures, s, "sunInk/card", p.sunInk, p.card, 3.0)
            check(failures, s, "accent/card", p.accent, p.card, 3.0)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun windPanel() {
        val failures = mutableListOf<String>()
        for ((sky, time) in scenes) {
            val p = palette(sky, time)
            for ((name, bg) in listOf("top" to p.windCard, "bottom" to p.windCardEnd)) {
                val s = "$sky/$time wind $name"
                check(failures, s, "onWind", p.onWind, bg, 4.5)
                check(failures, s, "onWindVariant", p.onWindVariant, bg, 4.5)
                check(failures, s, "gust", ScenePalette.SUN, bg, 4.5)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
