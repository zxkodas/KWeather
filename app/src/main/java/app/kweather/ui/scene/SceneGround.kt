package app.kweather.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.sin

/**
 * The horizon: a distant ridge whose contrast follows visibility, the far and near ground, and a row of
 * grass blades. Blades lean with the mean wind and sway further downwind with gusts, in waves that travel
 * the way the wind blows. Snow leaves short stubs.
 */
internal class GroundLayer(private val s: Stage) {
    private val p = s.p
    private val u = s.u
    private val w = s.w
    private val h = s.h
    private val top = s.ground

    /** Low, wide, uneven hills far away. Mostly air color; clearer days show more of the land. */
    private val ridge: Path? = if (s.clarity < 0.12f) null else Path().apply {
        fun y(x: Float): Float {
            val f = x / w
            val shape = 0.6f * noise(f * 2.3f, 141) + 0.3f * noise(f * 6f, 142) + 0.1f * noise(f * 17f, 143)
            return top + 4f * u - (6f + 30f * shape) * u
        }
        moveTo(0f, y(0f))
        var x = 0f
        while (x < w) {
            x = min(x + 6f * u, w)
            lineTo(x, y(x))
        }
        lineTo(w, top + 20f * u)
        lineTo(0f, top + 20f * u)
        close()
    }
    private val ridgeColor = when {
        s.night -> lerpColor(s.horizon, Color.Black, 0.2f + 0.2f * s.clarity)
        s.snow -> lerpColor(s.horizon, Color(0xFFE8EEF5), 0.35f)
        else -> lerpColor(s.horizon, p.groundFar, 0.15f + 0.35f * s.clarity)
    }

    /** Air between the viewer and the ridge: a band of horizon color that thickens as visibility drops. */
    private val haze = Brush.verticalGradient(
        0f to s.horizon.copy(alpha = 0f),
        1f to s.horizon.copy(alpha = 0.25f + 0.5f * (1f - s.clarity)),
        startY = top - 44f * u,
        endY = top + 8f * u,
    )

    private val far = Path().apply {
        moveTo(0f, top + 6f * u)
        cubicTo(w * 0.3f, top - 8f * u, w * 0.6f, top + 10f * u, w, top - 2f * u)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    private val farBrush = Brush.verticalGradient(
        0f to lerpColor(p.groundFar, s.horizon, 0.35f * (1f - s.clarity)),
        1f to p.groundFar,
        startY = top - 8f * u,
        endY = top + 26f * u,
    )

    private val nearTop = top + 22f * u
    private fun nearY(x: Float) = nearTop + sin(x / w * 2.4f + 0.6f) * 5f * u + (noise(x / w * 9f, 151) - 0.5f) * 3f * u
    private val near = Path().apply {
        moveTo(0f, nearY(0f))
        var x = 0f
        while (x < w) {
            x = min(x + 8f * u, w)
            lineTo(x, nearY(x))
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    private val nearColor = if (s.snow) lerpColor(p.groundFar, Color.White, 0.35f) else p.grass
    private val nearBrush = Brush.verticalGradient(
        0f to nearColor,
        1f to lerpColor(nearColor, Color.Black, if (s.snow) 0.06f else 0.2f),
        startY = nearTop - 6f * u,
        endY = h,
    )

    private class Blade(val x: Float, val baseY: Float, val height: Float, val width: Float, val shade: Int, val jitter: Float, val phase: Float)

    private val bladeShades: List<Color> = run {
        val rim = s.lowSun && s.openSky
        when {
            s.snow -> listOf(p.grass, p.grass, p.grass)
            s.night -> listOf(p.grass, lerpColor(p.grass, Color.Black, 0.15f), p.grass)
            rim -> listOf(lerpColor(p.grass, Color.Black, 0.18f), p.grass, lerpColor(p.grass, Color(0xFFFFB27A), 0.38f))
            else -> listOf(lerpColor(p.grass, Color.Black, 0.18f), p.grass, lerpColor(p.grass, p.groundFar, 0.35f))
        }
    }

    private val blades: List<Blade> = run {
        val count = if (s.snow) 70 else 160
        List(count) { i ->
            val x = (i + rnd(i, 90)) / count * w
            // Grass grows in clumps: a slow noise sets how tall each stretch is.
            val clump = 0.55f + 0.9f * noise(x / w * 11f, 152)
            val height = if (s.snow) (4f + rnd(i, 91) * 8f) * u else (10f + rnd(i, 91) * 20f) * clump * u
            Blade(
                x = x,
                baseY = nearY(x) + 3f * u,
                height = height,
                width = (0.9f + rnd(i, 95) * 0.9f) * u,
                shade = i % 3,
                jitter = (rnd(i, 93) - 0.5f) * 0.16f,
                phase = rnd(i, 92) * 0.8f,
            )
        }
    }

    /** One combined shape per shade, rebuilt each frame: three draw calls instead of one per blade. */
    private val bladePaths = List(3) { Path() }

    fun DrawScope.draw(t: Float) {
        ridge?.let {
            drawPath(it, ridgeColor)
            drawRect(haze, topLeft = Offset(0f, top - 44f * u), size = androidx.compose.ui.geometry.Size(w, 52f * u))
        }
        drawPath(far, farBrush)
        drawPath(near, nearBrush)

        val dir = s.windSign
        val speed = s.spec.windSpeed.toFloat()
        val strength = (speed / 60f).coerceAtMost(1f)
        val gustExcess = (s.spec.windGusts.toFloat() - speed).coerceAtLeast(0f)
        val lean = s.windEast * (0.1f + 0.6f * strength)
        val sway = (0.04f + 0.3f * (gustExcess / 40f).coerceAtMost(1f)) * (if (speed < 2f) 0.5f else 1f)
        val freq = 1.1f + 1.6f * strength
        bladePaths.forEach { it.reset() }
        for (b in blades) {
            // sin(kx - wt) travels toward +x, so flip k for wind blowing west.
            val wave = 0.5f + 0.5f * sin(dir * b.x / w * 7f - t * freq + b.phase)
            val bend = (lean + b.jitter + dir * sway * wave).coerceIn(-0.9f, 0.9f)
            val tip = Offset(b.x + bend * b.height, b.baseY - b.height * (1f - 0.45f * bend * bend))
            val ctrl = Offset(b.x + bend * b.height * 0.15f, b.baseY - b.height * 0.6f)
            val bw = b.width
            val blade = bladePaths[b.shade]
            blade.moveTo(b.x - bw, b.baseY)
            blade.quadraticTo(ctrl.x - bw * 0.5f, ctrl.y, tip.x, tip.y)
            blade.quadraticTo(ctrl.x + bw * 0.5f, ctrl.y, b.x + bw, b.baseY)
            blade.close()
        }
        for (k in 0..2) drawPath(bladePaths[k], bladeShades[k])
    }

    private val lip = Path().apply {
        moveTo(0f, h - 18f * u)
        cubicTo(110f * u, h - 26f * u, 260f * u, h - 22f * u, w, h - 14f * u)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }

    /** The page background rising into the scene, so the cards below read as part of it. */
    fun DrawScope.drawLip() {
        drawPath(lip, p.background)
    }
}
