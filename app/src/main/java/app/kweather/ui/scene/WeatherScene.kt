package app.kweather.ui.scene

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated sky for the current conditions, drawn in code. Positions are authored on a 390-unit-wide stage
 * and scaled to the real width.
 *
 * Everything that moves follows the weather: clouds, rain, snow, fog and grass go with the real wind.
 * [textBottom] is where the hero text ends (px from the top), so low clouds can stay below it.
 * With [motion] false the scene holds still on its current frame; pagers pass false for pages that aren't
 * current, so only one scene animates during a swipe.
 *
 * Shapes, gradients and the soft cloud sprites are built once per size and spec in [Stage]; each frame
 * only positions them, so the draw pass allocates almost nothing. Building a stage takes tens of ms for a
 * covered sky (more on a phone), so it happens on a background thread: until it is ready the previous
 * stage keeps drawing, or a plain sky gradient on first show, and swiping between places doesn't stutter.
 */
@Composable
fun WeatherScene(
    spec: SceneSpec,
    palette: ScenePalette,
    modifier: Modifier = Modifier,
    textBottom: Float = 0f,
    motion: Boolean = true,
) {
    val context = LocalContext.current
    val systemAllows = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    // Both the app setting and Android's "remove animations" can turn motion off.
    val animate = motion && systemAllows && app.kweather.ui.settings.LocalFmt.current.settings.animations
    val time = remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            // Resume where a pause left off, so clouds and rain don't jump when a page becomes current again.
            val resumeAt = time.floatValue
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { time.floatValue = resumeAt + (it - start) / 1_000_000_000f }
            }
        }
    }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var stage by remember { mutableStateOf<Stage?>(null) }
    LaunchedEffect(spec, palette, area, textBottom) {
        if (area == IntSize.Zero) return@LaunchedEffect
        // A newer spec or size restarts this effect, and a stage built for the old one is dropped.
        stage = withContext(Dispatchers.Default) { Stage(spec, palette, area.toSize(), textBottom) }
    }
    Spacer(
        modifier
            .onSizeChanged { area = it }
            .drawWithCache {
                val placeholder = Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom))
                // Reading the time and the stage here only invalidates drawing, never recomposition or the cache.
                onDrawBehind {
                    val ready = stage
                    if (ready != null && ready.size == size) {
                        drawStage(ready, time.floatValue)
                    } else {
                        drawRect(placeholder)
                    }
                }
            },
    )
}

/** One frame of the scene at time [t] seconds. Builds a fresh [Stage], so use it for stills and tests. */
internal fun DrawScope.drawScene(spec: SceneSpec, palette: ScenePalette, t: Float, textBottom: Float) {
    drawStage(Stage(spec, palette, size, textBottom), t)
}

/** Top of the far ground, in stage units above the bottom edge. */
internal const val GROUND_TOP = 64f

/** Everything about the scene that doesn't change from frame to frame. */
internal class Stage(val spec: SceneSpec, val p: ScenePalette, val size: Size, val textBottom: Float) {
    val w = size.width
    val h = size.height
    val u = w / 390f
    val ground = h - GROUND_TOP * u

    val night = spec.time == TimeOfDay.Night
    val lowSun = spec.time == TimeOfDay.Dawn || spec.time == TimeOfDay.Dusk
    val openSky = spec.sky == SkyType.Clear || spec.sky == SkyType.PartlyCloudy
    val covered = spec.sky in setOf(SkyType.Overcast, SkyType.Drizzle, SkyType.Rain, SkyType.Storm, SkyType.Snow)
    val snow = spec.sky == SkyType.Snow

    /** Horizontal component of the wind: positive = blowing toward the east (screen right). */
    val windEast = sin((spec.windDirection + 180) * PI / 180).toFloat()
    val windFactor = (spec.windSpeed / 40.0).toFloat().coerceIn(0f, 1.5f)
    val windSign = if (windEast < 0) -1f else 1f

    /** Px per second for the nearest clouds; farther layers move a fraction of this. */
    val cloudSpeed = (6f + spec.windSpeed.toFloat() * 0.6f) * u * windSign

    val sunCenter = when (spec.time) {
        TimeOfDay.Dawn -> Offset(90f * u, ground - 6f * u)
        TimeOfDay.Dusk -> Offset(300f * u, ground - 6f * u)
        else -> Offset(300f * u, 140f * u)
    }
    val moonCenter = Offset(300f * u, 130f * u)

    /** 0 = thick haze, 1 = crystal clear. From visibility when known, else a typical value for the sky. */
    val clarity: Float = run {
        val typical = when (spec.sky) {
            SkyType.Clear -> 0.85f
            SkyType.PartlyCloudy -> 0.8f
            SkyType.Overcast -> 0.55f
            SkyType.Drizzle -> 0.45f
            SkyType.Rain -> 0.35f
            SkyType.Storm -> 0.3f
            SkyType.Snow -> 0.35f
            SkyType.Fog -> 0.05f
        }
        val v = spec.visibility ?: return@run typical
        val fromVis = ((v - 500.0) / 24_000.0).toFloat().coerceIn(0f, 1f)
        if (spec.sky == SkyType.Fog) min(fromVis, 0.15f) else fromVis
    }

    val horizon = ScenePalette.horizon(p.skyBottom)

    /** Where light comes from, as a unit vector in screen space (y down). Shades every cloud the same way. */
    val light: Offset = when {
        night || covered || spec.sky == SkyType.Fog -> Offset(0f, -1f)
        spec.time == TimeOfDay.Dawn -> Offset(-0.85f, -0.53f)
        spec.time == TimeOfDay.Dusk -> Offset(0.85f, -0.53f)
        else -> Offset(0.45f, -0.89f)
    }

    val sky = SkyLayer(this)
    val clouds = CloudLayer(this)
    val groundLayer = GroundLayer(this)
    val weather = WeatherLayer(this)
}

internal fun DrawScope.drawStage(s: Stage, t: Float) {
    with(s.sky) {
        drawBackground()
        drawTwinkle(t)
        drawShootingStar(t)
    }
    if (s.lowSun && s.openSky) with(s.sky) { drawSun() }
    with(s.clouds) { drawDeck(t) }
    drawHeroScrim(s.p, s.textBottom)
    with(s.clouds) { drawLow(t) }
    // Sun and moon go over the veil so they keep their color; no cloud reaches their height.
    if (s.openSky && !s.night && !s.lowSun) with(s.sky) { drawSun() }
    if (s.openSky && s.night) with(s.sky) { drawMoon() }
    if (s.spec.sky == SkyType.Storm) with(s.weather) { drawLightning(t) }
    with(s.clouds) { drawRainShafts(t) }
    with(s.groundLayer) { draw(t) }
    with(s.weather) { draw(t) }
    drawGrain()
    with(s.groundLayer) { drawLip() }
}

/**
 * The veil behind the hero text: solid down to [textBottom], then fading out. Its color and strength come
 * from the palette, which sizes it so [ScenePalette.heroInk] reaches 4.5:1 over the sky and clouds.
 */
private fun DrawScope.drawHeroScrim(p: ScenePalette, textBottom: Float) {
    if (p.heroScrim.alpha == 0f || textBottom <= 0f) return
    val total = textBottom + 72.dp.toPx()
    drawRect(
        Brush.verticalGradient(
            0f to p.heroScrim,
            textBottom / total to p.heroScrim,
            1f to p.heroScrim.copy(alpha = 0f),
            endY = total,
        ),
        size = Size(size.width, total),
    )
}

/** Deterministic pseudo-random in [0, 1) so particles and shapes keep their identity frame to frame. */
internal fun rnd(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (v - floor(v)).toFloat()
}

internal fun wrap(value: Float, span: Float): Float = ((value % span) + span) % span

/** Smooth 1D value noise in [0, 1]. With [period] > 0 it repeats every [period] units, for seamless tiles. */
internal fun noise(x: Float, seed: Int, period: Int = 0): Float {
    val i = floor(x).toInt()
    val f = x - i
    val s = f * f * (3 - 2 * f)
    fun at(k: Int) = rnd(if (period > 0) Math.floorMod(k, period) else k, seed)
    return at(i) + (at(i + 1) - at(i)) * s
}

/** Billowy noise: rounded lumps with sharp creases between them, like the underside of a cloud. */
internal fun billow(x: Float, seed: Int, period: Int = 0): Float = abs(noise(x, seed, period) * 2f - 1f)

internal fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
