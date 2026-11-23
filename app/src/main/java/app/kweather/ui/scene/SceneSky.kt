package app.kweather.ui.scene

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

private val WARM = Color(0xFFFFB27A)

/** Sky gradient, the light the sun or moon throws into it, stars, and the sun and moon themselves. */
internal class SkyLayer(private val s: Stage) {
    private val p = s.p
    private val u = s.u

    private val skyBrush = Brush.verticalGradient(
        0f to p.skyTop,
        ScenePalette.SKY_MID to ScenePalette.skyAt(p.skyTop, p.skyBottom, ScenePalette.SKY_MID),
        1f to s.horizon,
        endY = s.ground + 30f * u,
    )

    /** Light the sun spreads through the air around it: broad and faint, so it reads as air, not a halo. */
    private val bloom: Brush? = if (s.night || !s.openSky) null else {
        val tint = if (s.lowSun) Color(0xFFFFC48A) else Color(0xFFFFF3D2)
        val a = if (s.lowSun) 0.42f else 0.32f
        Brush.radialGradient(
            0f to tint.copy(alpha = a),
            0.18f to tint.copy(alpha = a * 0.62f),
            0.42f to tint.copy(alpha = a * 0.24f),
            0.7f to tint.copy(alpha = a * 0.07f),
            1f to tint.copy(alpha = 0f),
            center = s.sunCenter,
            radius = (if (s.lowSun) 300f else 210f) * u,
        )
    }

    /** Warm band lying along the horizon under a low sun; weaker when clouds block it. */
    private val horizonGlow: Brush? = if (!s.lowSun) null else {
        val a = if (s.openSky) 0.55f else 0.22f
        Brush.radialGradient(
            0f to WARM.copy(alpha = a),
            0.35f to WARM.copy(alpha = a * 0.5f),
            1f to WARM.copy(alpha = 0f),
            center = Offset(s.sunCenter.x, s.ground),
            radius = 220f * u,
        )
    }

    /** Air light: haze whitens the lower sky toward the horizon color as visibility drops. */
    private val airLight = Brush.verticalGradient(
        0f to s.horizon.copy(alpha = 0f),
        1f to lerpColor(s.horizon, Color.White, 0.25f).copy(alpha = 0.6f * (1f - s.clarity)),
        startY = s.ground - 190f * u,
        endY = s.ground,
    )

    private class Star(val c: Offset, val r: Float, val alpha: Float, val color: Color, val rate: Float, val phase: Float)

    /** Many faint stars, a few bright ones, thinning toward the glow of the horizon. */
    private val stars: List<Star> = if (!s.night) emptyList() else {
        val top = s.h * 0.66f
        List(110) { i ->
            val y = rnd(i, 2).pow(1.35f) * top
            val mag = rnd(i, 5).pow(3.2f)
            val tint = when {
                rnd(i, 6) < 0.18f -> Color(0xFFFFE2C4)
                rnd(i, 6) < 0.36f -> Color(0xFFD2DEFF)
                else -> Color.White
            }
            Star(
                c = Offset(rnd(i, 1) * s.w, y),
                r = (0.55f + mag * 1.5f) * u,
                alpha = (0.3f + 0.7f * rnd(i, 7).pow(1.6f)) * (1f - 0.75f * y / top),
                color = tint,
                rate = 0.5f + rnd(i, 3) * 1.2f,
                phase = rnd(i, 4) * 6f,
            )
        }
    }
    private val showStars = s.night && (s.openSky || s.snow && s.spec.precipitation < 0.2)

    /**
     * The still star field, drawn once at full resolution (stars are too small for the half-resolution
     * sprites) so a night sky costs one draw per frame instead of one per star.
     */
    private val starField: Sprite? = if (!showStars) null else
        softSprite(s.w, s.h * 0.66f, Offset.Zero, scale = 1f) { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            for (star in stars) {
                paint.color = star.color.copy(alpha = star.alpha).toArgb()
                c.drawCircle(star.c.x, star.c.y, star.r, paint)
                if (star.r > 1.4f * u) {
                    paint.color = star.color.copy(alpha = star.alpha * 0.12f).toArgb()
                    c.drawCircle(star.c.x, star.c.y, star.r * 3.2f, paint)
                }
            }
        }

    /** Only the brightest stars shimmer, drawn over the still field so the twinkle stays subtle. */
    private val twinklers = if (showStars) stars.filter { it.r > 1.4f * u } else emptyList()

    fun DrawScope.drawBackground() {
        drawRect(skyBrush)
        drawRect(airLight)
        bloom?.let { drawRect(it) }
        starField?.let { with(it) { draw(Offset.Zero) } }
        horizonGlow?.let { glow ->
            scale(scaleX = 1.9f, scaleY = 0.5f, pivot = Offset(s.sunCenter.x, s.ground)) {
                drawCircle(glow, radius = 220f * u, center = Offset(s.sunCenter.x, s.ground))
            }
        }
    }

    fun DrawScope.drawTwinkle(t: Float) {
        for (star in twinklers) {
            val k = 0.5f + 0.5f * sin(t * star.rate + star.phase)
            drawCircle(star.color, radius = star.r * 0.9f, center = star.c, alpha = star.alpha * 0.45f * k)
        }
    }

    private val sunCore = if (s.lowSun) {
        Brush.radialGradient(
            0f to Color(0xFFFFE7B0), 0.6f to Color(0xFFFFB774), 1f to Color(0xFFFF9A5E),
            center = s.sunCenter, radius = 44f * u,
        )
    } else {
        Brush.radialGradient(
            0f to Color(0xFFFFFCEB), 0.55f to Color(0xFFFFF0B8), 1f to Color(0xFFFFDC78),
            center = s.sunCenter, radius = 40f * u,
        )
    }
    private val rimTint = if (s.lowSun) Color(0xFFFFC890) else Color(0xFFFFF4CC)
    private val sunRim = Brush.radialGradient(
        0f to rimTint.copy(alpha = 0.6f),
        0.68f to rimTint.copy(alpha = 0.6f),
        0.8f to rimTint.copy(alpha = 0.22f),
        1f to rimTint.copy(alpha = 0f),
        center = s.sunCenter,
        radius = 56f * u,
    )

    /** A still sun: warm core, a soft glow hugging it, and the bloom already in the sky. Half set when low. */
    fun DrawScope.drawSun() {
        drawCircle(sunRim, radius = 56f * u, center = s.sunCenter)
        if (s.lowSun) {
            // A low sun looks slightly squashed by the air it shines through.
            scale(scaleX = 1f, scaleY = 0.93f, pivot = s.sunCenter) {
                drawCircle(sunCore, radius = 44f * u, center = s.sunCenter)
            }
        } else {
            drawCircle(sunCore, radius = 40f * u, center = s.sunCenter)
        }
    }

    private val moonR = 34f * u
    private val moonLit: Path
    private val litFraction: Float

    init {
        val c = s.moonCenter
        val r = moonR
        val phase = s.spec.moonPhase
        val k = cos(2 * PI * phase).toFloat()
        litFraction = (1f - k) / 2f
        // Waxing lights the right side in the north, the left side in the south.
        val litRight = (phase <= 0.5) != s.spec.southern
        val half = Path().apply {
            addArc(Rect(c, r), if (litRight) -90f else 90f, 180f)
            close()
        }
        val ellipse = Path().apply { addOval(Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r)) }
        moonLit = Path.combine(if (k > 0f) PathOperation.Difference else PathOperation.Union, half, ellipse)
    }

    private val moonGlow = Brush.radialGradient(
        0f to Color(0xFFC9D3FF).copy(alpha = 0.3f),
        0.35f to Color(0xFFC9D3FF).copy(alpha = 0.12f),
        1f to Color(0xFFC9D3FF).copy(alpha = 0f),
        center = s.moonCenter,
        radius = moonR * 3.4f,
    )
    private val moonFace = Brush.radialGradient(
        0f to Color(0xFFFBF8EA), 0.8f to Color(0xFFF0EAD2), 1f to Color(0xFFDCD3B8),
        center = s.moonCenter, radius = moonR,
    )

    /**
     * The moon with a true terminator for its phase. The dark part shows faint earthshine instead of being
     * painted over with sky, and a few soft maria keep the face from looking like a flat disc.
     */
    fun DrawScope.drawMoon() {
        val c = s.moonCenter
        val r = moonR
        val glowScale = 0.35f + 0.65f * litFraction
        drawCircle(moonGlow, radius = r * 3.4f, center = c, alpha = glowScale)
        drawCircle(lerpColor(p.skyTop, Color(0xFFA9B4D6), 0.22f), radius = r, center = c)
        clipPath(moonLit) {
            drawCircle(moonFace, radius = r, center = c)
            for (i in 0 until 5) {
                val mc = c + Offset((rnd(i, 101) - 0.55f) * r * 1.1f, (rnd(i, 102) - 0.6f) * r * 1.0f)
                val mr = r * (0.18f + rnd(i, 103) * 0.24f)
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFC9C6B8).copy(alpha = 0.32f),
                        0.6f to Color(0xFFC9C6B8).copy(alpha = 0.18f),
                        1f to Color(0xFFC9C6B8).copy(alpha = 0f),
                        center = mc,
                        radius = mr,
                    ),
                    radius = mr,
                    center = mc,
                )
            }
        }
    }

    /** A rare shooting star: about half of all 40 s windows get one, at a random moment and place. */
    fun DrawScope.drawShootingStar(t: Float) {
        if (!showStars) return
        val window = floor(t / 40f).toInt()
        if (rnd(window, 41) >= 0.5f) return
        val k = (t - (window * 40f + 2f + rnd(window, 42) * 36f)) / 0.7f
        if (k !in 0f..1f) return
        val head = Offset((250f + rnd(window, 43) * 100f) * u - k * 180f * u, (40f + rnd(window, 44) * 60f) * u + k * 90f * u)
        val tail = head + Offset(44f * u, -22f * u)
        drawLine(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 1f - k), Color.White.copy(alpha = 0f)), start = head, end = tail),
            head,
            tail,
            strokeWidth = 1.4f * u,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A faint film of noise over the whole scene. At 2% or less it is too weak to read as texture; it breaks up
 * the banding that long sky gradients show on 8-bit screens.
 */
internal fun DrawScope.drawGrain() {
    drawRect(GrainBrush.brush, size = Size(size.width, size.height))
}

private object GrainBrush {
    val brush: ShaderBrush by lazy {
        val n = 128
        val pixels = IntArray(n * n) { i ->
            val v = rnd(i, 211)
            val a = (abs(v - 0.5f) * 2f * 5f).toInt()
            val rgb = if (v < 0.5f) 0x000000 else 0xFFFFFF
            (a shl 24) or rgb
        }
        val bitmap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}
