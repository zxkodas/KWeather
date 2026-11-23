package app.kweather.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rain, snow, fog and lightning: everything that falls or drifts in front of the land. */
internal class WeatherLayer(private val s: Stage) {
    private val p = s.p
    private val u = s.u

    /** Falling streaks in three depths: far ones thin, faint and slow, near ones long, bright and fast. */
    private class RainDepth(val share: Float, val length: Float, val width: Float, val alpha: Float, val speed: Float)

    private val rainDepths = listOf(
        RainDepth(0.5f, 0.55f, 0.8f, 0.32f, 0.75f),
        RainDepth(0.32f, 1f, 1.3f, 0.5f, 1f),
        RainDepth(0.18f, 1.5f, 1.9f, 0.62f, 1.3f),
    )

    private val rain: Pair<Int, Float>? = when (s.spec.sky) {
        SkyType.Drizzle -> 70 to 14f
        SkyType.Rain, SkyType.Storm ->
            (90 + s.spec.precipitation * 35).toInt().coerceAtMost(if (s.spec.heavy) 300 else 230) to 28f
        else -> null
    }

    /** One tail and one head segment per drop, x0 y0 x1 y1 each, reused every frame. */
    private val rainTails = rainDepths.map { FloatArray(((rain?.first ?: 0) * it.share).roundToInt() * 4) }
    private val rainHeads = rainDepths.map { FloatArray(((rain?.first ?: 0) * it.share).roundToInt() * 4) }
    private val rainPaints = rainDepths.map { depth ->
        listOf(0.3f, 0.87f).map { k ->
            Paint().apply {
                color = p.precipitation
                alpha = depth.alpha * k
                strokeWidth = depth.width * u
                strokeCap = StrokeCap.Round
            }
        }
    }

    /**
     * All drops of one depth go out in two batched calls (faint tails, brighter heads) instead of two calls
     * per drop: with two pages on screen during a swipe, per-drop calls overloaded the main thread.
     */
    private fun DrawScope.drawRain(t: Float, count: Int, length: Float) {
        val slant = s.windEast * s.windFactor * 0.35f
        var index = 0
        for ((d, depth) in rainDepths.withIndex()) {
            val n = (count * depth.share).roundToInt()
            val len = length * depth.length * u
            val h = size.height + len
            val tails = rainTails[d]
            val heads = rainHeads[d]
            for (k in 0 until n) {
                val i = index++
                val speed = (620f + rnd(i, 11) * 260f) * u * depth.speed
                val y = wrap(rnd(i, 12) * h + t * speed, h) - len
                val x = wrap(rnd(i, 13) * size.width * 1.3f + y * slant, size.width * 1.3f) - size.width * 0.15f
                val midX = x + len * slant * 0.45f
                val midY = y + len * 0.45f
                tails[k * 4] = x; tails[k * 4 + 1] = y; tails[k * 4 + 2] = midX; tails[k * 4 + 3] = midY
                heads[k * 4] = midX; heads[k * 4 + 1] = midY; heads[k * 4 + 2] = x + len * slant; heads[k * 4 + 3] = y + len
            }
            drawIntoCanvas { c ->
                c.drawRawPoints(PointMode.Lines, tails, rainPaints[d][0])
                c.drawRawPoints(PointMode.Lines, heads, rainPaints[d][1])
            }
        }
        // Splashes on the near ground: little rings that open and fade, more of them in harder rain.
        val splashes = (count / 6).coerceAtMost(40)
        val groundY = s.ground + 26f * u
        repeat(splashes) { i ->
            val k = wrap(t * (1.6f + rnd(i, 15)) + rnd(i, 16), 1f)
            if (k > 0.35f) return@repeat
            val f = k / 0.35f
            val c = Offset(rnd(i, 17) * size.width, groundY + rnd(i, 18) * 18f * u)
            val r = (2f + f * 6f) * u
            drawOval(
                p.precipitation,
                topLeft = Offset(c.x - r, c.y - r * 0.3f),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 0.6f),
                alpha = 0.5f * (1f - f),
                style = Stroke(width = 1f * u),
            )
        }
    }

    private class SnowDepth(val share: Float, val radius: Float, val alpha: Float, val speed: Float, val soft: Boolean)

    private val snowDepths = listOf(
        SnowDepth(0.5f, 0.9f, 0.55f, 0.6f, false),
        SnowDepth(0.35f, 1.6f, 0.8f, 1f, false),
        SnowDepth(0.15f, 2.8f, 0.9f, 1.5f, true),
    )

    private val flakeGlow = Color.White

    private val snowCount = (70 + s.spec.precipitation * 45).toInt().coerceAtMost(200)

    /** Flakes of one depth come in two sizes (by index parity); each size is one batched call of round points. */
    private val snowPoints: List<List<FloatArray>> = run {
        var base = 0
        snowDepths.map { d ->
            val n = (snowCount * d.share).roundToInt()
            val even = (base until base + n).count { it % 2 == 0 }
            base += n
            listOf(FloatArray(even * 2), FloatArray((n - even) * 2))
        }
    }
    private val snowPaints = snowDepths.map { d ->
        List(2) { k ->
            Paint().apply {
                color = p.precipitation
                alpha = d.alpha * 0.85f
                strokeWidth = 2f * d.radius * (0.85f + 0.4f * k) * u
                strokeCap = StrokeCap.Round
            }
        }
    }
    private val snowGlow = Paint().apply {
        color = flakeGlow
        alpha = snowDepths.last().alpha * 0.22f
        strokeWidth = 2f * snowDepths.last().radius * 1.9f * u
        strokeCap = StrokeCap.Round
    }

    private val filledScratch = IntArray(2)

    private fun DrawScope.drawSnow(t: Float) {
        val h = size.height + 10f * u
        val drift = s.windEast * (20f + s.windFactor * 160f) * u
        val span = size.width + 120f * u
        var index = 0
        for ((d, depth) in snowDepths.withIndex()) {
            val n = (snowCount * depth.share).roundToInt()
            val filled = filledScratch.apply { fill(0) }
            for (k in 0 until n) {
                val i = index++
                val speed = (40f + rnd(i, 21) * 50f) * u * (1f + s.windFactor * 0.6f) * depth.speed
                val y = wrap(rnd(i, 22) * h + t * speed, h)
                val sway = sin(t * (0.8f + rnd(i, 23)) + rnd(i, 24) * 6f) * 10f * u * depth.speed
                val x = wrap(rnd(i, 25) * span + t * drift * depth.speed + sway, span) - 60f * u
                val b = i % 2
                val arr = snowPoints[d][b]
                arr[filled[b] * 2] = x
                arr[filled[b] * 2 + 1] = y
                filled[b]++
            }
            drawIntoCanvas { c ->
                for (b in 0..1) {
                    val pts = snowPoints[d][b]
                    if (depth.soft) c.drawRawPoints(PointMode.Points, pts, snowGlow)
                    c.drawRawPoints(PointMode.Points, pts, snowPaints[d][b])
                }
            }
        }
        if (s.windFactor > 0.75f) {
            // Blowing snow streaks near the ground.
            repeat(8) { i ->
                val y = size.height - (40f + rnd(i, 31) * 90f) * u
                val x = wrap(rnd(i, 32) * span + t * 260f * u * s.windSign, span) - 60f * u
                val end = Offset(x + 70f * u * s.windSign, y - 2f * u)
                drawLine(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.5f)), start = Offset(x, y), end = end),
                    Offset(x, y),
                    end,
                    strokeWidth = 1.2f * u,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    private val fogColor = lerpColor(p.skyBottom, Color.White, 0.5f)

    private class Veil(val sprite: Sprite, val y: Float, val parallax: Float, val alpha: Float, val offset: Float)

    /** Long soft banks of fog at different heights, thicker near the ground, drifting with the wind. */
    private val veils: List<Veil> = if (s.spec.sky != SkyType.Fog) emptyList() else {
        val start = (s.textBottom - 20f * u).coerceAtLeast(s.h * 0.35f)
        List(4) { i ->
            val f = i / 3f
            val y = start + f * (s.ground - start)
            Veil(veilSprite(51 + i, s.w * 1.4f, (34f + 22f * f) * u, 12f * u, fogColor), y, 0.5f + 0.25f * i, 0.4f + 0.14f * i, rnd(i, 52))
        }
    }

    private val groundFog = Brush.verticalGradient(
        0f to fogColor.copy(alpha = 0f),
        0.6f to fogColor.copy(alpha = 0.55f),
        1f to fogColor.copy(alpha = 0.7f),
        startY = s.ground - 90f * u,
        endY = s.h,
    )

    private fun DrawScope.drawFog(t: Float) {
        val speed = (4f + s.spec.windSpeed.toFloat() * 0.5f) * u * s.windSign
        for (v in veils) {
            val period = v.sprite.width
            var x = wrap(t * speed * v.parallax + v.offset * period, period) - period
            while (x < size.width) {
                with(v.sprite) { draw(Offset(x, v.y), v.alpha) }
                x += period
            }
        }
        drawRect(groundFog, topLeft = Offset(0f, s.ground - 90f * u))
    }

    fun DrawScope.draw(t: Float) {
        rain?.let { (count, length) -> drawRain(t, count, length) }
        if (s.snow) drawSnow(t)
        if (s.spec.sky == SkyType.Fog) drawFog(t)
    }

    /**
     * Strikes come in 2.5 s slots, each with its own chance, so the gaps are irregular; more often in heavy
     * storms. Each strike is a branching bolt from the deck to the ground that lights the clouds around it.
     */
    fun DrawScope.drawLightning(t: Float) {
        val slot = floor(t / 2.5f).toInt()
        if (rnd(slot, 51) >= (if (s.spec.heavy) 0.45f else 0.3f)) return
        val local = t - slot * 2.5f - rnd(slot, 52) * 1.6f
        val strength = when {
            local in 0f..0.06f -> 1f
            local in 0.06f..0.1f -> 0.25f
            local in 0.1f..0.16f -> 0.9f
            else -> 0f
        }
        if (strength == 0f) return
        val x0 = (120f + rnd(slot, 53) * 220f) * u
        val y0 = 120f * u
        drawRect(Color(0xFFC9D6F0), alpha = 0.1f * strength)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFDDE5FF).copy(alpha = 0.55f * strength),
                0.5f to Color(0xFFDDE5FF).copy(alpha = 0.18f * strength),
                1f to Color(0xFFDDE5FF).copy(alpha = 0f),
                center = Offset(x0, y0),
                radius = 240f * u,
            ),
            radius = 240f * u,
            center = Offset(x0, y0),
        )
        val bolt = boltPath(slot, Offset(x0, y0), s.ground + 4f * u)
        val core = Color(0xFFFFFCF0)
        drawPath(bolt, Color(0xFFD7DEFF), alpha = 0.12f * strength, style = Stroke(9f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(bolt, Color(0xFFE6EBFF), alpha = 0.3f * strength, style = Stroke(4f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(bolt, core, alpha = strength, style = Stroke(1.6f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    /** A jagged main channel to the ground plus two side branches, seeded by the strike's slot. */
    private fun boltPath(seed: Int, start: Offset, groundY: Float): Path {
        val path = Path()
        val steps = 14
        val points = ArrayList<Offset>(steps + 1)
        var x = start.x
        for (k in 0..steps) {
            val y = start.y + (groundY - start.y) * k / steps
            if (k > 0) x += (rnd(seed * 31 + k, 54) - 0.5f) * 26f * u
            points += Offset(x, y)
        }
        path.moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        repeat(2) { b ->
            val from = points[2 + (rnd(seed * 7 + b, 55) * (steps / 2)).toInt()]
            val dir = if (rnd(seed * 7 + b, 56) < 0.5f) -1f else 1f
            path.moveTo(from.x, from.y)
            var bx = from.x
            var by = from.y
            repeat(5) { k ->
                bx += dir * (8f + rnd(seed * 11 + b * 5 + k, 57) * 14f) * u
                by += (10f + rnd(seed * 13 + b * 5 + k, 58) * 14f) * u
                path.lineTo(bx, by)
            }
        }
        return path
    }
}

/**
 * A horizontal fog bank built from overlapping soft clumps of uneven size and density, so it has visible
 * structure that slides past as it drifts. Repeats seamlessly every [period] px.
 */
internal fun veilSprite(seed: Int, period: Float, thickness: Float, blur: Float, color: Color): Sprite {
    val ps = (period * SPRITE_SCALE).roundToInt()
    val realPeriod = ps / SPRITE_SCALE
    val height = thickness * 2 + 4 * blur
    val mid = height / 2
    return softSprite(realPeriod, height, Offset(0f, mid)) { c ->
        repeat(22) { k ->
            val x = rnd(seed * 31 + k, 61) * realPeriod
            val rx = realPeriod * (0.04f + 0.08f * rnd(seed * 31 + k, 62))
            val ry = thickness * (0.3f + 0.45f * rnd(seed * 31 + k, 63))
            val y = mid + (rnd(seed * 31 + k, 64) - 0.45f) * thickness * 0.7f
            val paint = fillPaint(color.copy(alpha = 0.3f + 0.6f * rnd(seed * 31 + k, 65)), blur = blur)
            // Drawn a period to each side as well, so clumps that cross the seam wrap around.
            for (shift in floatArrayOf(-realPeriod, 0f, realPeriod)) {
                c.drawOval(x + shift - rx, y - ry, x + shift + rx, y + ry, paint)
            }
        }
    }
}

