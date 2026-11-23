package app.kweather.ui.scene

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated sky for the current conditions. All art is drawn in code from a few shared layers.
 * Positions are authored on a 390-unit-wide stage and scaled to the real width.
 *
 * Everything that moves follows the weather: clouds, rain, snow and grass go with the real wind.
 * [textBottom] is where the hero text ends (px from the top), so low clouds can stay below it.
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
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
            }
        }
    }
    // Reading the time inside the draw lambda only invalidates drawing, never recomposition.
    Canvas(modifier) { drawScene(spec, palette, time.floatValue, textBottom) }
}

/** Top of the far ground, in stage units above the bottom edge. */
private const val GROUND_TOP = 64f

private fun DrawScope.drawScene(spec: SceneSpec, p: ScenePalette, t: Float, textBottom: Float) {
    val u = size.width / 390f
    val ground = size.height - GROUND_TOP * u
    drawSky(spec, p, u, ground)

    val night = spec.time == TimeOfDay.Night
    val openSky = spec.sky == SkyType.Clear || spec.sky == SkyType.PartlyCloudy
    // Horizontal drift from wind: positive = blowing toward the east (screen right).
    val windEast = sin((spec.windDirection + 180) * PI / 180).toFloat()
    val windFactor = (spec.windSpeed / 40.0).toFloat().coerceIn(0f, 1.5f)

    if (night && (openSky || spec.sky == SkyType.Snow && spec.precipitation < 0.2)) drawStars(u, t)
    drawClouds(spec, p, u, t, windEast, textBottom, ground)
    drawHeroScrim(p, textBottom)
    // Sun and moon go over the veil so they keep their color; no cloud reaches their height any more.
    if (openSky && !night) drawSun(spec, u, ground)
    if (openSky && night) drawMoon(spec.moonPhase, spec.southern, p, u)

    if (spec.sky == SkyType.Storm) drawLightning(spec.heavy, u, t)
    drawGround(spec, p, u, t, windEast)
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
    drawLip(p, u)
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

/** Sky gradient with a lighter horizon band; at dawn and dusk a warm glow sits on the horizon under the sun. */
private fun DrawScope.drawSky(spec: SceneSpec, p: ScenePalette, u: Float, ground: Float) {
    val mid = ScenePalette.SKY_MID
    drawRect(
        Brush.verticalGradient(
            0f to p.skyTop,
            mid to ScenePalette.skyAt(p.skyTop, p.skyBottom, mid),
            1f to ScenePalette.horizon(p.skyBottom),
        ),
    )
    if (spec.time == TimeOfDay.Dawn || spec.time == TimeOfDay.Dusk) {
        val center = Offset(if (spec.time == TimeOfDay.Dawn) 90f * u else 300f * u, ground)
        val open = spec.sky == SkyType.Clear || spec.sky == SkyType.PartlyCloudy
        val warm = Color(0xFFFFB27A)
        val r = 200f * u
        scale(scaleX = 1.8f, scaleY = 0.55f, pivot = center) {
            drawCircle(
                Brush.radialGradient(
                    0f to warm.copy(alpha = if (open) 0.5f else 0.22f),
                    1f to warm.copy(alpha = 0f),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
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
    // A rare shooting star: about half of all 40 s windows get one, at a random moment and place.
    val window = floor(t / 40f).toInt()
    if (rnd(window, 41) < 0.5f) {
        val k = (t - (window * 40f + 2f + rnd(window, 42) * 36f)) / 0.7f
        if (k in 0f..1f) {
            val start = Offset((250f + rnd(window, 43) * 100f) * u - k * 180f * u, (40f + rnd(window, 44) * 60f) * u + k * 90f * u)
            drawLine(Color.White, start, start + Offset(36f * u, -18f * u), strokeWidth = 1.5f * u,
                cap = StrokeCap.Round, alpha = 1f - k)
        }
    }
}

/** A still sun with one soft halo. Low at dawn and dusk, half set behind the ground. */
private fun DrawScope.drawSun(spec: SceneSpec, u: Float, ground: Float) {
    val center = when (spec.time) {
        TimeOfDay.Dawn -> Offset(90f * u, ground - 6f * u)
        TimeOfDay.Dusk -> Offset(300f * u, ground - 6f * u)
        else -> Offset(300f * u, 140f * u)
    }
    val low = spec.time != TimeOfDay.Day
    val core = if (low) Color(0xFFFFB86B) else Color(0xFFFFD45C)
    val halo = if (low) Color(0xFFFFC98F) else Color(0xFFFFE9A8)
    val glow = 110f * u
    drawCircle(
        Brush.radialGradient(
            0f to halo.copy(alpha = 0.7f),
            0.45f to halo.copy(alpha = 0.3f),
            1f to halo.copy(alpha = 0f),
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
    drawCircle(core, radius = (if (low) 40f else 46f) * u, center = center)
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

private fun DrawScope.drawClouds(
    spec: SceneSpec,
    p: ScenePalette,
    u: Float,
    t: Float,
    windEast: Float,
    textBottom: Float,
    ground: Float,
) {
    val dir = if (windEast < 0) -1f else 1f
    val speed = (6f + spec.windSpeed.toFloat() * 0.6f) * u * dir
    val span = size.width + 360f * u
    fun x(i: Int, factor: Float) = wrap(rnd(i, 9) * span + t * speed * factor, span) - 180f * u
    // Low clouds sit in the band between the hero text and the horizon.
    val lowTop = (textBottom + 56f * u).coerceAtMost(ground - 8f * u)
    fun lowY(i: Int) = lowTop + rnd(i, 7) * (ground - 8f * u - lowTop)

    when (spec.sky) {
        SkyType.Overcast, SkyType.Drizzle, SkyType.Rain, SkyType.Storm, SkyType.Snow -> {
            drawDeck(p, u, t, speed)
            // Ragged low cloud under the deck, moving faster than it.
            repeat(3) { i ->
                val y = (150f + rnd(i, 61) * 90f) * u
                drawPuff(Offset(x(i + 20, 1.8f), y), u * (0.45f + rnd(i, 60) * 0.3f), i + 20, p.cloudShade, 0.85f)
            }
        }
        SkyType.Clear -> if (spec.time != TimeOfDay.Night) {
            repeat(2) { i ->
                val scale = 0.7f + rnd(i, 8) * 0.7f
                drawOval(p.cloud, Offset(x(i, 0.6f) - 70f * u * scale, lowY(i)), Size(140f * u * scale, 20f * u * scale), alpha = 0.6f)
            }
        }
        SkyType.PartlyCloudy -> repeat(4) { i ->
            val layer = i % 3
            val color = if (layer == 0) p.cloudShade else p.cloud
            drawPuff(Offset(x(i, 0.6f + layer * 0.3f), lowY(i)), u * (0.6f + rnd(i, 8) * 0.5f), i, color, 0.94f)
        }
        SkyType.Fog -> repeat(2) { i ->
            drawPuff(Offset(x(i, 0.5f), (40f + rnd(i, 7) * 120f) * u), u * (0.8f + rnd(i, 8) * 0.5f), i, p.cloud, 0.5f)
        }
    }
}

/** A cumulus of 3 to 6 seeded puffs on a flat base. One path, so overlaps don't show darker. */
private fun DrawScope.drawPuff(c: Offset, s: Float, seed: Int, color: Color, alpha: Float) {
    val n = 3 + (rnd(seed, 70) * 4).toInt()
    val width = (150f + rnd(seed, 71) * 70f) * s
    val left = c.x - width / 2
    val path = Path()
    path.addRoundRect(RoundRect(left, c.y - 18f * s, left + width, c.y + 8f * s, CornerRadius(13f * s)))
    for (k in 0 until n) {
        val f = (k + 0.5f) / n
        val r = (18f + sin(f * PI).toFloat() * 26f + rnd(seed * 7 + k, 72) * 10f) * s
        val px = left + f * width + (rnd(seed * 7 + k, 73) - 0.5f) * 14f * s
        path.addOval(Rect(Offset(px, c.y - r * 0.55f), r))
    }
    drawPath(path, color, alpha = alpha)
}

/** Covered sky: three scalloped layers across the top, the lowest one darkest, drifting with the wind. */
private fun DrawScope.drawDeck(p: ScenePalette, u: Float, t: Float, speed: Float) {
    val layers = listOf(
        Triple(190f, p.cloudShade, 0.55f),
        Triple(135f, lerpColor(p.cloudShade, p.cloud, 0.5f), 0.75f),
        Triple(80f, p.cloud, 1f),
    )
    val step = 46f * u
    layers.forEachIndexed { k, (depth, color, drift) ->
        val edge = depth * u
        val shift = t * speed * drift
        val path = Path()
        path.addRect(Rect(0f, 0f, size.width, edge))
        var j = floor(-shift / step).toInt() - 1
        while (true) {
            val x = j * step + shift
            if (x > size.width + step) break
            val r = (30f + rnd(j, 80 + k) * 22f) * u
            path.addOval(Rect(Offset(x, edge - r * 0.35f), r))
            j++
        }
        drawPath(path, color, alpha = 0.96f)
    }
}

/** Strikes come in 2.5 s slots, each with its own chance, so the gaps are irregular; shorter in heavy storms. */
private fun DrawScope.drawLightning(heavy: Boolean, u: Float, t: Float) {
    val slot = floor(t / 2.5f).toInt()
    if (rnd(slot, 51) >= (if (heavy) 0.45f else 0.3f)) return
    val local = t - slot * 2.5f - rnd(slot, 52) * 1.6f
    val strength = when {
        local in 0f..0.06f -> 1f
        local in 0.06f..0.1f -> 0.25f
        local in 0.1f..0.16f -> 0.9f
        else -> 0f
    }
    if (strength == 0f) return
    drawRect(Color(0xFFC9D6F0), alpha = 0.35f * strength)
    val dx = (rnd(slot, 53) - 0.5f) * 160f * u
    val bolt = Path().apply {
        moveTo(238f * u + dx, 110f * u)
        lineTo(214f * u + dx, 186f * u)
        lineTo(232f * u + dx, 186f * u)
        lineTo(206f * u + dx, 270f * u)
        lineTo(262f * u + dx, 168f * u)
        lineTo(242f * u + dx, 168f * u)
        lineTo(262f * u + dx, 110f * u)
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
    repeat(7) { i ->
        val y = size.height * (0.35f + i * 0.1f)
        val x = sin(t * 0.15f + i) * 40f * u
        drawOval(lerpColor(p.skyBottom, Color.White, 0.5f), Offset(x - 80f * u, y),
            Size(size.width + 160f * u, 46f * u), alpha = 0.32f)
    }
}

/**
 * The horizon: far ground, near ground and a row of grass blades. Blades lean with the mean wind and
 * sway further downwind with gusts, in waves that travel the way the wind blows. Snow leaves short stubs.
 */
private fun DrawScope.drawGround(spec: SceneSpec, p: ScenePalette, u: Float, t: Float, windEast: Float) {
    val w = size.width
    val h = size.height
    val top = h - GROUND_TOP * u
    val far = Path().apply {
        moveTo(0f, top + 6f * u)
        cubicTo(w * 0.3f, top - 8f * u, w * 0.6f, top + 10f * u, w, top - 2f * u)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(far, p.groundFar)

    val nearTop = top + 22f * u
    fun nearY(x: Float) = nearTop + sin(x / w * 2.4f + 0.6f) * 5f * u
    val near = Path().apply {
        moveTo(0f, nearY(0f))
        var x = 0f
        while (x < w) {
            x = min(x + 12f * u, w)
            lineTo(x, nearY(x))
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val snow = spec.sky == SkyType.Snow
    drawPath(near, if (snow) lerpColor(p.groundFar, Color.White, 0.35f) else p.grass)

    val dir = if (windEast < 0) -1f else 1f
    val speed = spec.windSpeed.toFloat()
    val strength = (speed / 60f).coerceAtMost(1f)
    val gustExcess = (spec.windGusts.toFloat() - speed).coerceAtLeast(0f)
    val lean = windEast * (0.1f + 0.6f * strength)
    val sway = (0.04f + 0.3f * (gustExcess / 40f).coerceAtMost(1f)) * (if (speed < 2f) 0.5f else 1f)
    val freq = 1.1f + 1.6f * strength
    val count = if (snow) 70 else 150
    val shades = if (snow) {
        listOf(p.grass, p.grass, p.grass)
    } else {
        listOf(lerpColor(p.grass, Color.Black, 0.18f), p.grass, lerpColor(p.grass, p.groundFar, 0.35f))
    }
    val bw = 1.3f * u
    val blade = Path()
    repeat(count) { i ->
        val x = (i + rnd(i, 90)) / count * w
        val base = Offset(x, nearY(x) + 3f * u)
        val height = (if (snow) 4f + rnd(i, 91) * 8f else 12f + rnd(i, 91) * 22f) * u
        // sin(kx - wt) travels toward +x, so flip k for wind blowing west.
        val wave = 0.5f + 0.5f * sin(dir * x / w * 7f - t * freq + rnd(i, 92) * 0.8f)
        val bend = (lean + (rnd(i, 93) - 0.5f) * 0.16f + dir * sway * wave).coerceIn(-0.9f, 0.9f)
        val tip = Offset(base.x + bend * height, base.y - height * (1f - 0.45f * bend * bend))
        val ctrl = Offset(base.x + bend * height * 0.15f, base.y - height * 0.6f)
        blade.reset()
        blade.moveTo(base.x - bw, base.y)
        blade.quadraticTo(ctrl.x - bw * 0.5f, ctrl.y, tip.x, tip.y)
        blade.quadraticTo(ctrl.x + bw * 0.5f, ctrl.y, base.x + bw, base.y)
        blade.close()
        drawPath(blade, shades[i % 3])
    }
}

/** The page background rising into the scene, so the cards below read as part of it. */
private fun DrawScope.drawLip(p: ScenePalette, u: Float) {
    val h = size.height
    val w = size.width
    val path = Path().apply {
        moveTo(0f, h - 18f * u)
        cubicTo(110f * u, h - 26f * u, 260f * u, h - 22f * u, w, h - 14f * u)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path, p.background)
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, min(1f, t))
