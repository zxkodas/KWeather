package app.kweather.ui.scene

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated sky for the current conditions. All art is drawn in code from a few shared layers.
 * Positions are authored on a 390-unit-wide stage and scaled to the real width.
 */
@Composable
fun WeatherScene(spec: SceneSpec, palette: ScenePalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val systemAllows = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    // Both the app setting and Android's "remove animations" can turn motion off.
    val animate = systemAllows && app.kweather.ui.settings.LocalFmt.current.settings.animations
    val time = remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
            }
        }
    }
    // Reading the time inside the draw lambda only invalidates drawing, never recomposition.
    Canvas(modifier) { drawScene(spec, palette, time.floatValue) }
}

private fun DrawScope.drawScene(spec: SceneSpec, p: ScenePalette, t: Float) {
    val u = size.width / 390f
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom)))

    val night = spec.time == TimeOfDay.Night
    val openSky = spec.sky == SkyType.Clear || spec.sky == SkyType.PartlyCloudy
    // Horizontal drift from wind: positive = blowing toward the east (screen right).
    val windEast = sin((spec.windDirection + 180) * PI / 180).toFloat()
    val windFactor = (spec.windSpeed / 40.0).toFloat().coerceIn(0f, 1.5f)

    if (night && (openSky || spec.sky == SkyType.Snow && spec.precipitation < 0.2)) drawStars(u, t)
    if (openSky && !night) drawSun(spec, u, t)
    if (openSky && night) drawMoon(spec.moonPhase, spec.southern, p, u)

    drawClouds(spec, p, u, t, windEast)

    if (spec.sky == SkyType.Storm) drawLightning(u, t)
    when (spec.sky) {
        SkyType.Drizzle -> drawRain(p, u, t, count = 45, length = 14f, windEast * windFactor)
        SkyType.Rain, SkyType.Storm -> {
            val count = (70 + spec.precipitation * 30).toInt().coerceAtMost(if (spec.heavy) 260 else 200)
            drawRain(p, u, t, count, length = 30f, windEast * windFactor)
        }
        SkyType.Snow -> {
            val count = (60 + spec.precipitation * 40).toInt().coerceAtMost(180)
            drawSnow(p, u, t, count, windEast, windFactor)
        }
        else -> Unit
    }
    if (spec.sky == SkyType.Fog) drawFog(p, u, t)

    drawHills(p, u)
}

/** Deterministic pseudo-random in [0, 1) so particles keep their identity frame to frame. */
private fun rnd(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (v - floor(v)).toFloat()
}

private fun wrap(value: Float, span: Float): Float = ((value % span) + span) % span

private fun DrawScope.drawStars(u: Float, t: Float) {
    val h = size.height * 0.62f
    repeat(48) { i ->
        val x = rnd(i, 1) * size.width
        val y = rnd(i, 2) * h
        val twinkle = 0.35f + 0.65f * abs(sin(t * (0.6f + rnd(i, 3) * 1.4f) + rnd(i, 4) * 6f))
        drawCircle(Color.White, radius = (0.7f + rnd(i, 5) * 1.1f) * u, center = Offset(x, y), alpha = twinkle)
    }
    // An occasional shooting star.
    val cycle = wrap(t, 11f)
    if (cycle in 10f..10.6f) {
        val k = (cycle - 10f) / 0.6f
        val start = Offset(330f * u - k * 180f * u, 60f * u + k * 90f * u)
        drawLine(Color.White, start, start + Offset(36f * u, -18f * u), strokeWidth = 1.5f * u,
            cap = StrokeCap.Round, alpha = 1f - k)
    }
}

private fun DrawScope.drawSun(spec: SceneSpec, u: Float, t: Float) {
    val center = when (spec.time) {
        TimeOfDay.Dawn -> Offset(90f * u, size.height * 0.62f)
        TimeOfDay.Dusk -> Offset(300f * u, size.height * 0.62f)
        else -> Offset(300f * u, 140f * u)
    }
    val low = spec.time != TimeOfDay.Day
    val core = if (low) Color(0xFFFFB86B) else Color(0xFFFFD45C)
    val halo = if (low) Color(0xFFFFC98F) else Color(0xFFFFE9A8)
    val pulse = 1f + 0.06f * sin(t * 1.4f)
    val glow = 110f * u * pulse
    drawCircle(
        Brush.radialGradient(
            0f to halo.copy(alpha = 0.75f),
            0.45f to halo.copy(alpha = 0.35f),
            1f to halo.copy(alpha = 0f),
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
    if (!low) {
        rotate(degrees = t * 6f, pivot = center) {
            repeat(8) { i ->
                val a = i * PI / 4
                val from = center + Offset((cos(a) * 66 * u).toFloat(), (sin(a) * 66 * u).toFloat())
                val to = center + Offset((cos(a) * 82 * u).toFloat(), (sin(a) * 82 * u).toFloat())
                drawLine(Color(0xFFFFE08A), from, to, strokeWidth = 4f * u, cap = StrokeCap.Round, alpha = 0.9f)
            }
        }
    }
    drawCircle(core, radius = 46f * u, center = center)
}

private fun DrawScope.drawMoon(phase: Double, southern: Boolean, p: ScenePalette, u: Float) {
    val center = Offset(300f * u, 130f * u)
    val r = 36f * u
    val glow = r * 2.6f
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFC9D3FF).copy(alpha = 0.32f),
            0.4f to Color(0xFFC9D3FF).copy(alpha = 0.14f),
            1f to Color(0xFFC9D3FF).copy(alpha = 0f),
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    clipPath(disc) {
        drawCircle(Color(0xFFF4F1DE), radius = r, center = center)
        // Shadow disc slides across: waxing lights the right side, waning the left.
        val north = if (phase <= 0.5) -(phase * 4 * r).toFloat() else ((1 - phase) * 4 * r).toFloat()
        val shift = if (southern) -north else north
        if (abs(shift) < 2 * r) {
            drawCircle(lerpColor(p.skyTop, p.skyBottom, 0.35f), radius = r * 1.02f,
                center = center + Offset(shift, 0f), alpha = 0.94f)
        }
    }
}

private fun DrawScope.drawClouds(spec: SceneSpec, p: ScenePalette, u: Float, t: Float, windEast: Float) {
    val count = when (spec.sky) {
        SkyType.Clear -> if (spec.time == TimeOfDay.Night) 0 else 2
        SkyType.PartlyCloudy -> 4
        SkyType.Fog -> 2
        SkyType.Overcast, SkyType.Drizzle, SkyType.Rain, SkyType.Snow -> 7
        SkyType.Storm -> 8
    }
    val wispy = spec.sky == SkyType.Clear
    val speed = (6f + spec.windSpeed.toFloat() * 0.6f) * u * (if (windEast < 0) -1f else 1f)
    val span = size.width + 320f * u
    repeat(count) { i ->
        val layer = i % 3
        val baseY = when (spec.sky) {
            // Keep clear-sky wisps low, under the temperature text.
            SkyType.Clear -> size.height - (150f + rnd(i, 7) * 25f) * u
            SkyType.PartlyCloudy -> (200f + rnd(i, 7) * 140f) * u
            else -> (30f + rnd(i, 7) * 170f) * u
        }
        val scale = (0.7f + rnd(i, 8) * 0.7f) * (1f + layer * 0.15f)
        val x = wrap(rnd(i, 9) * span + t * speed * (0.6f + layer * 0.3f), span) - 160f * u
        val color = if (layer == 0) p.cloudShade else p.cloud
        val alpha = when {
            wispy -> 0.6f
            spec.sky == SkyType.PartlyCloudy -> 0.92f
            else -> 0.95f
        }
        if (wispy) {
            drawOval(color, Offset(x - 70f * u * scale, baseY), Size(140f * u * scale, 22f * u * scale), alpha = alpha)
        } else {
            drawCloud(Offset(x, baseY), 1f * u * scale, color, alpha)
        }
    }
}

private fun DrawScope.drawCloud(c: Offset, s: Float, color: Color, alpha: Float) {
    drawOval(color, c + Offset(-90f * s, -10f * s), Size(180f * s, 56f * s), alpha = alpha)
    drawCircle(color, radius = 38f * s, center = c + Offset(-30f * s, -14f * s), alpha = alpha)
    drawCircle(color, radius = 48f * s, center = c + Offset(22f * s, -24f * s), alpha = alpha)
}

private fun DrawScope.drawLightning(u: Float, t: Float) {
    val cycle = wrap(t, 7f) / 7f
    val strength = when {
        cycle in 0.86f..0.875f -> 1f
        cycle in 0.875f..0.885f -> 0.25f
        cycle in 0.885f..0.9f -> 0.9f
        else -> 0f
    }
    if (strength == 0f) return
    drawRect(Color(0xFFC9D6F0), alpha = 0.35f * strength)
    val bolt = Path().apply {
        moveTo(238f * u, 110f * u)
        lineTo(214f * u, 186f * u)
        lineTo(232f * u, 186f * u)
        lineTo(206f * u, 270f * u)
        lineTo(262f * u, 168f * u)
        lineTo(242f * u, 168f * u)
        lineTo(262f * u, 110f * u)
        close()
    }
    drawPath(bolt, Color(0xFFFFF3C4), alpha = strength)
}

private fun DrawScope.drawRain(p: ScenePalette, u: Float, t: Float, count: Int, length: Float, slant: Float) {
    val dx = slant * 0.35f
    val h = size.height + length * u
    repeat(count) { i ->
        val speed = (620f + rnd(i, 11) * 260f) * u
        val y = wrap(rnd(i, 12) * h + t * speed, h) - length * u
        val x = wrap(rnd(i, 13) * size.width * 1.3f + y * dx, size.width * 1.3f) - size.width * 0.15f
        drawLine(p.precipitation, Offset(x, y), Offset(x + length * u * dx, y + length * u),
            strokeWidth = 1.4f * u, cap = StrokeCap.Round, alpha = 0.55f + rnd(i, 14) * 0.3f)
    }
}

private fun DrawScope.drawSnow(p: ScenePalette, u: Float, t: Float, count: Int, windEast: Float, windFactor: Float) {
    val h = size.height + 10f * u
    val drift = windEast * (20f + windFactor * 160f) * u
    val span = size.width + 120f * u
    repeat(count) { i ->
        val speed = (40f + rnd(i, 21) * 50f) * u * (1f + windFactor * 0.6f)
        val y = wrap(rnd(i, 22) * h + t * speed, h)
        val sway = sin(t * (0.8f + rnd(i, 23)) + rnd(i, 24) * 6f) * 10f * u
        val x = wrap(rnd(i, 25) * span + t * drift + sway, span) - 60f * u
        drawCircle(p.precipitation, radius = (1.2f + rnd(i, 26) * 1.8f) * u, center = Offset(x, y),
            alpha = 0.6f + rnd(i, 27) * 0.4f)
    }
    if (windFactor > 0.75f) {
        // Blowing snow streaks near the ground.
        repeat(6) { i ->
            val y = size.height - (40f + rnd(i, 31) * 90f) * u
            val x = wrap(rnd(i, 32) * span + t * 260f * u * (if (windEast < 0) -1f else 1f), span) - 60f * u
            drawLine(Color.White, Offset(x, y), Offset(x + 70f * u, y - 2f * u), strokeWidth = 1.2f * u,
                cap = StrokeCap.Round, alpha = 0.5f)
        }
    }
}

private fun DrawScope.drawFog(p: ScenePalette, u: Float, t: Float) {
    repeat(6) { i ->
        val y = size.height * (0.35f + i * 0.1f)
        val x = sin(t * 0.15f + i) * 40f * u
        drawOval(lerpColor(p.skyBottom, Color.White, 0.5f), Offset(x - 80f * u, y),
            Size(size.width + 160f * u, 46f * u), alpha = 0.32f)
    }
}

private fun DrawScope.drawHills(p: ScenePalette, u: Float) {
    val h = size.height
    val w = size.width
    fun hill(color: Color, base: Float, build: Path.() -> Unit) {
        val path = Path().apply {
            moveTo(0f, h - base * u)
            build()
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
    }
    hill(p.hillFar, 120f) {
        cubicTo(70f * u, h - 160f * u, 140f * u, h - 150f * u, 210f * u, h - 128f * u)
        cubicTo(280f * u, h - 108f * u, 340f * u, h - 144f * u, w, h - 136f * u)
    }
    hill(p.hillMid, 78f) {
        cubicTo(90f * u, h - 100f * u, 170f * u, h - 88f * u, 250f * u, h - 70f * u)
        cubicTo(320f * u, h - 54f * u, 360f * u, h - 70f * u, w, h - 78f * u)
    }
    hill(p.background, 28f) {
        cubicTo(110f * u, h - 40f * u, 260f * u, h - 36f * u, w, h - 26f * u)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, min(1f, t))
