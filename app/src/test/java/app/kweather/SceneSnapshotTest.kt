package app.kweather

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.scene.SkyType
import app.kweather.ui.scene.Stage
import app.kweather.ui.scene.TimeOfDay
import app.kweather.ui.scene.drawScene
import app.kweather.ui.scene.drawStage
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the hero scene to PNGs for design review (not a pass/fail test). Output goes to
 * `app/build/scene-snapshots/<set>/`, where the set name comes from the SCENE_SET environment variable.
 *
 *     SCENE_SET=after ./gradlew testDebugUnitTest --tests 'app.kweather.SceneSnapshotTest.render'
 *
 * [animate] writes a few seconds of frames per scene to `app/build/scene-frames/<scene>/` (encode them with
 * ffmpeg), and [buildCost] times how long each scene takes to prepare. Both only run when asked:
 *
 *     SCENE_FRAMES=1 ./gradlew testDebugUnitTest --tests 'app.kweather.SceneSnapshotTest.animate'
 *     SCENE_TIMING=1 ./gradlew testDebugUnitTest --tests 'app.kweather.SceneSnapshotTest.buildCost'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SceneSnapshotTest {
    private val density = 2.625f
    private val widthDp = 412f
    private val heightDp = 520f
    private val textBottomDp = 293f

    private fun spec(
        sky: SkyType,
        time: TimeOfDay,
        wind: Double = 14.0,
        from: Double = 250.0,
        precip: Double = 0.0,
        gusts: Double = 24.0,
        heavy: Boolean = false,
        moon: Double = 0.3,
        cover: Double = 50.0,
        visibility: Double? = null,
    ) = SceneSpec(sky, time, wind, from, precip, moon, heavy, southern = true, windGusts = gusts, cloudCover = cover, visibility = visibility)

    private val scenes: List<Pair<String, Pair<SceneSpec, Float>>> = listOf(
        "01-clear-day" to (spec(SkyType.Clear, TimeOfDay.Day) to 4f),
        "02-clear-dawn" to (spec(SkyType.Clear, TimeOfDay.Dawn, wind = 4.0, gusts = 6.0) to 4f),
        "03-clear-dusk" to (spec(SkyType.Clear, TimeOfDay.Dusk) to 4f),
        "04-clear-night" to (spec(SkyType.Clear, TimeOfDay.Night, moon = 0.36) to 4f),
        "05-partly-day-30" to (spec(SkyType.PartlyCloudy, TimeOfDay.Day, cover = 30.0) to 4f),
        "06-partly-dusk" to (spec(SkyType.PartlyCloudy, TimeOfDay.Dusk) to 4f),
        "07-overcast-day" to (spec(SkyType.Overcast, TimeOfDay.Day) to 4f),
        "08-drizzle-day" to (spec(SkyType.Drizzle, TimeOfDay.Day, precip = 0.3) to 4f),
        "09-rain-heavy" to (spec(SkyType.Rain, TimeOfDay.Day, wind = 38.0, gusts = 60.0, precip = 6.0, heavy = true) to 4f),
        "10-storm-night" to (spec(SkyType.Storm, TimeOfDay.Night, wind = 30.0, precip = 4.0, heavy = true) to stormFlash()),
        "11-snow-day" to (spec(SkyType.Snow, TimeOfDay.Day, precip = 1.0) to 4f),
        "12-snow-night" to (spec(SkyType.Snow, TimeOfDay.Night, precip = 0.1) to 4f),
        "13-fog-day" to (spec(SkyType.Fog, TimeOfDay.Day, wind = 5.0) to 4f),
        "14-fog-night" to (spec(SkyType.Fog, TimeOfDay.Night, wind = 5.0) to 4f),
        "15-gale-day" to (spec(SkyType.PartlyCloudy, TimeOfDay.Day, wind = 65.0, from = 270.0, gusts = 95.0) to 4f),
        "16-partly-day-70" to (spec(SkyType.PartlyCloudy, TimeOfDay.Day, cover = 70.0) to 4f),
        "17-partly-night" to (spec(SkyType.PartlyCloudy, TimeOfDay.Night, cover = 70.0, moon = 0.15) to 4f),
        "18-clear-day-hazy" to (spec(SkyType.Clear, TimeOfDay.Day, visibility = 4_000.0) to 4f),
    )

    /** First moment a heavy-storm lightning strike is at full strength, same slot logic as the scene. */
    private fun stormFlash(): Float {
        fun rnd(i: Int, salt: Int): Float {
            val v = kotlin.math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
            return (v - kotlin.math.floor(v)).toFloat()
        }
        var slot = 0
        while (rnd(slot, 51) >= 0.45f) slot++
        return slot * 2.5f + rnd(slot, 52) * 1.6f + 0.02f
    }

    @Test fun render() {
        val set = System.getenv("SCENE_SET") ?: "after"
        val dir = File("build/scene-snapshots/$set").apply { mkdirs() }
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        for ((name, pair) in scenes) {
            val (spec, t) = pair
            val palette = ScenePalette.forSpec(spec)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap.asImageBitmap())
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                drawScene(spec, palette, t, textBottomDp * density)
            }
            drawHeroText(android.graphics.Canvas(bitmap), palette.heroInk.toArgb(), density, spec.sky)
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** A stand-in for the hero text (city, temperature, condition, high/low) at the app's sizes. */
    private fun drawHeroText(c: android.graphics.Canvas, ink: Int, sp: Float, sky: SkyType) {
        // Plausible text for each sky, so the images read like the real app.
        val (temp, condition, range) = when (sky) {
            SkyType.Clear -> Triple("24°", "Clear", "H 27°  L 15°   Feels like 25°")
            SkyType.PartlyCloudy -> Triple("19°", "Partly cloudy", "H 22°  L 12°   Feels like 18°")
            SkyType.Overcast -> Triple("14°", "Overcast", "H 16°  L 10°   Feels like 13°")
            SkyType.Fog -> Triple("11°", "Fog", "H 15°  L 9°   Feels like 11°")
            SkyType.Drizzle -> Triple("13°", "Drizzle", "H 15°  L 10°   Feels like 12°")
            SkyType.Rain -> Triple("16°", "Heavy rain", "H 18°  L 13°   Feels like 14°")
            SkyType.Storm -> Triple("21°", "Thunderstorm", "H 26°  L 17°   Feels like 22°")
            SkyType.Snow -> Triple("-1°", "Snow", "H 1°  L -4°   Feels like -6°")
        }
        val left = 24f * sp
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        paint.textSize = 22f * sp
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("Buenos Aires", left, 110f * sp, paint)
        paint.textSize = 104f * sp
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(temp, left - 4f * sp, 222f * sp, paint)
        paint.textSize = 17f * sp
        c.drawText(condition, left, 254f * sp, paint)
        paint.textSize = 15f * sp
        paint.typeface = Typeface.DEFAULT
        c.drawText(range, left, 278f * sp, paint)
    }

    /** Scenes worth seeing in motion, with the time each clip starts at. */
    private val clips: List<Pair<String, Pair<SceneSpec, Float>>> = listOf(
        "gale-partly-cloudy" to (spec(SkyType.PartlyCloudy, TimeOfDay.Day, wind = 65.0, from = 270.0, gusts = 95.0, cover = 55.0) to 0f),
        "heavy-rain" to (spec(SkyType.Rain, TimeOfDay.Day, wind = 38.0, gusts = 60.0, precip = 6.0, heavy = true) to 0f),
        "storm-night" to (spec(SkyType.Storm, TimeOfDay.Night, wind = 30.0, precip = 4.0, heavy = true) to stormFlash() - 1.5f),
        "windy-snow" to (spec(SkyType.Snow, TimeOfDay.Day, wind = 35.0, gusts = 50.0, precip = 1.5) to 0f),
        "fog" to (spec(SkyType.Fog, TimeOfDay.Day, wind = 14.0) to 0f),
        "clear-dusk" to (spec(SkyType.Clear, TimeOfDay.Dusk, wind = 20.0, gusts = 40.0) to 0f),
    )

    @Test fun animate() {
        assumeTrue(System.getenv("SCENE_FRAMES") != null)
        val d = 1.5f
        val fps = 24
        val seconds = 4
        val w = (widthDp * d).toInt()
        val h = (heightDp * d).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap.asImageBitmap())
        // SCENE_FRAMES=fog renders just that clip; any other value renders all of them.
        val only = System.getenv("SCENE_FRAMES")
        for ((name, pair) in clips.filter { (n, _) -> clips.none { it.first == only } || n == only }) {
            val (spec, start) = pair
            val palette = ScenePalette.forSpec(spec)
            val dir = File("build/scene-frames/$name").apply { deleteRecursively(); mkdirs() }
            // One stage per clip, like the app: only the time changes from frame to frame.
            val stage = Stage(spec, palette, Size(w.toFloat(), h.toFloat()), textBottomDp * d)
            repeat(fps * seconds) { f ->
                CanvasDrawScope().draw(Density(d), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                    drawStage(stage, start + f / fps.toFloat())
                }
                drawHeroText(android.graphics.Canvas(bitmap), palette.heroInk.toArgb(), d, spec.sky)
                File(dir, "f%03d.png".format(f)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /**
     * Time to prepare each scene at phone size (paths, gradients and the soft cloud and fog shapes). In the app
     * this runs on the main thread when a page first draws or its weather changes, so it is a swipe stutter if slow.
     */
    @Test fun buildCost() {
        assumeTrue(System.getenv("SCENE_TIMING") != null)
        val size = Size(widthDp * density, heightDp * density)
        val lines = mutableListOf<String>()
        for ((name, pair) in scenes) {
            val spec = pair.first
            val palette = ScenePalette.forSpec(spec)
            repeat(2) { Stage(spec, palette, size, textBottomDp * density) } // warm up the JIT
            val runs = 5
            val start = System.nanoTime()
            repeat(runs) { Stage(spec, palette, size, textBottomDp * density) }
            val ms = (System.nanoTime() - start) / 1e6 / runs
            lines += "%-20s %6.1f ms".format(name, ms)
        }
        File("build/scene-snapshots").mkdirs()
        File("build/scene-snapshots/build-times.txt").writeText(lines.joinToString("\n") + "\n")
    }
}
