package app.kweather.ui.scene

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Soft shapes are rendered at half resolution: they are blurred anyway, and it keeps memory small. */
internal const val SPRITE_SCALE = 0.5f

/**
 * A shape rendered once into a bitmap and then only moved around. [scale] is bitmap px per real px (soft
 * shapes use half resolution). [anchor] is the point (in real px, from the bitmap's top left) that is placed
 * at the requested position.
 */
internal class Sprite(val image: ImageBitmap, val anchor: Offset = Offset.Zero, val scale: Float = SPRITE_SCALE) {
    val width = image.width / scale
    val height = image.height / scale
    private val dst = IntSize(width.roundToInt(), height.roundToInt())

    fun DrawScope.draw(at: Offset, alpha: Float = 1f) {
        translate(at.x - anchor.x, at.y - anchor.y) {
            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, dst, alpha = alpha, filterQuality = FilterQuality.Low)
        }
    }
}

internal fun softSprite(
    widthPx: Float,
    heightPx: Float,
    anchor: Offset,
    scale: Float = SPRITE_SCALE,
    paint: (android.graphics.Canvas) -> Unit,
): Sprite {
    val bitmap = Bitmap.createBitmap(
        max(1, (widthPx * scale).roundToInt()),
        max(1, (heightPx * scale).roundToInt()),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(scale, scale)
    paint(canvas)
    return Sprite(bitmap.asImageBitmap(), anchor, scale)
}

internal fun fillPaint(color: Color, blur: Float = 0f, mode: PorterDuff.Mode? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    if (blur > 0.5f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    if (mode != null) xfermode = PorterDuffXfermode(mode)
}

/** The three tones every cloud is painted with: sunlit crown, body, and shadowed underside. */
internal data class CloudLook(val lit: Color, val body: Color, val belly: Color) {
    fun toward(c: Color, t: Float) = CloudLook(lerpColor(lit, c, t), lerpColor(body, c, t), lerpColor(belly, c, t))
}

/**
 * A cumulus: seeded lobes of uneven size on a flat base, with small cauliflower lumps on the big ones.
 * The body is painted soft-edged, the crown is lit from [light], and the base falls into shadow.
 * [soft] widens the blur for ragged scud under a deck. The sprite's anchor is the middle of its base.
 */
internal fun cumulusSprite(seed: Int, width: Float, look: CloudLook, light: Offset, soft: Float = 1f): Sprite {
    val pad = width * 0.14f
    val height = width * 0.62f
    val baseY = pad + height
    class Lobe(val x: Float, val y: Float, val r: Float)

    val lobes = mutableListOf<Lobe>()
    val n = 4 + (rnd(seed, 70) * 3).toInt()
    for (k in 0 until n) {
        val f = (k + 0.5f) / n
        val bell = sin(f * PI).toFloat().pow(0.8f)
        val r = width * (0.09f + 0.2f * bell + 0.06f * rnd(seed * 7 + k, 72))
        val x = pad + width * (0.1f + 0.8f * f) + (rnd(seed * 7 + k, 73) - 0.5f) * width * 0.06f
        val y = baseY - r * 0.55f - bell * width * (0.08f + 0.06f * rnd(seed * 7 + k, 76))
        lobes += Lobe(x, y, r)
        if (bell > 0.62f) {
            repeat(2) { m ->
                val a = -PI / 2 + (rnd(seed * 13 + k * 3 + m, 74) - 0.5f) * 1.7
                val cr = r * (0.38f + 0.2f * rnd(seed * 13 + k * 3 + m, 75))
                lobes += Lobe(x + (kotlin.math.cos(a) * r * 0.64f).toFloat(), y + (sin(a) * r * 0.64f).toFloat(), cr)
            }
        }
    }
    val minR = lobes.take(n).minOf { it.r }
    val shape = android.graphics.Path().apply {
        lobes.forEach { addCircle(it.x, it.y, it.r, android.graphics.Path.Direction.CW) }
        addRoundRect(lobes.first().x, baseY - minR * 0.7f, lobes[n - 1].x, baseY, minR * 0.5f, minR * 0.5f, android.graphics.Path.Direction.CW)
        val below = android.graphics.Path().apply { addRect(0f, 0f, width + 2 * pad, baseY, android.graphics.Path.Direction.CW) }
        op(below, android.graphics.Path.Op.INTERSECT)
    }
    return softSprite(width + 2 * pad, baseY + pad, Offset(pad + width / 2, baseY)) { c ->
        val body = fillPaint(look.body, blur = width * 0.006f * soft)
        // Lumps are shaded a little lighter than the cloud's body, so their crescents stay a hint.
        val lump = fillPaint(lerpColor(look.lit, look.body, 0.6f), blur = width * 0.006f * soft)
        val lit = fillPaint(look.lit, blur = width * 0.01f * soft)
        c.save()
        c.clipRect(0f, 0f, width + 2 * pad, baseY)
        c.drawPath(shape, body)
        // Lumps back to front, each a body disc with a lit disc nudged toward the light on top. The sliver
        // of body left showing is a shadow crescent on the far side of every lump; lower lumps overlap the
        // ones above them, so the cloud builds up in layers like a painted cumulus.
        lobes.sortedBy { it.y }.forEach {
            c.drawCircle(it.x, it.y, it.r, lump)
            c.drawCircle(it.x + light.x * it.r * 0.16f, it.y + light.y * it.r * 0.16f, it.r * 0.86f, lit)
        }
        c.restore()
        val belly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, baseY - height * 0.32f, 0f, baseY, look.belly.copy(alpha = 0f).toArgb(), look.belly.toArgb(), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, width + 2 * pad, baseY + pad, belly)
    }
}

/**
 * A small broken streak of cloud for a mostly clear sky: a chain of soft blobs along a slight curve, thick in
 * the middle and frayed at the ends, lit on top like the cumulus. Anchor is the middle.
 */
internal fun wispSprite(seed: Int, width: Float, look: CloudLook, light: Offset): Sprite {
    val pad = width * 0.1f
    val height = width * 0.22f
    val mid = pad + height * 0.55f
    val sag = height * (rnd(seed, 87) - 0.5f) * 0.5f
    class Blob(val x: Float, val y: Float, val rx: Float, val ry: Float)

    val n = 13
    val blobs = List(n) { k ->
        val f = ((k + 0.5f + (rnd(seed * 17 + k, 81) - 0.5f) * 0.7f) / n).coerceIn(0f, 1f)
        val taper = sin(f * PI).toFloat().pow(0.9f)
        // Some blobs drop out almost entirely, so the streak breaks up instead of forming a bar.
        val gap = if (rnd(seed * 17 + k, 82) < 0.2f) 0.45f else 1f
        val ry = height * (0.1f + 0.24f * taper) * (0.7f + 0.6f * rnd(seed * 17 + k, 83)) * gap
        Blob(
            x = pad + f * width,
            y = mid + sag * sin(f * PI).toFloat() + (rnd(seed * 17 + k, 84) - 0.5f) * height * 0.22f,
            rx = ry * (1.8f + 1.2f * rnd(seed * 17 + k, 85)),
            ry = ry,
        )
    }
    return softSprite(width + 2 * pad, height + 2 * pad, Offset(pad + width / 2, mid)) { c ->
        // Thin cloud is shaded only faintly: a soft belly, never a dark rim along the edge.
        val body = fillPaint(lerpColor(look.lit, look.body, 0.45f), blur = height * 0.07f)
        blobs.forEach { c.drawOval(it.x - it.rx, it.y - it.ry, it.x + it.rx, it.y + it.ry, body) }
        val lit = fillPaint(look.lit, blur = height * 0.1f, mode = PorterDuff.Mode.SRC_ATOP)
        blobs.forEach {
            val cx = it.x + light.x * it.ry * 0.15f
            val cy = it.y + light.y * it.ry * 0.25f
            c.drawOval(cx - it.rx * 0.95f, cy - it.ry * 0.85f, cx + it.rx * 0.95f, cy + it.ry * 0.85f, lit)
        }
    }
}

/**
 * One layer of a covered sky: hangs from the top edge down to [depth] with a lumpy, soft lower edge, and
 * repeats seamlessly every [Sprite.width] px so it can drift forever. Darker at the top, lighter at the edge.
 */
internal fun deckSprite(seed: Int, period: Float, depth: Float, amp: Float, blur: Float, top: Color, edge: Color, rim: Color): Sprite {
    val ps = (period * SPRITE_SCALE).roundToInt()
    val realPeriod = ps / SPRITE_SCALE
    val height = depth + amp + 3 * blur
    fun edgeY(x: Float): Float {
        val f = x / realPeriod
        val lumps = 0.55f * billow(f * 7f, seed, 7) + 0.3f * billow(f * 15f, seed + 1, 15) + 0.15f * noise(f * 37f, seed + 2, 37)
        return depth - amp * 0.3f + amp * lumps
    }
    fun outline(lift: Float) = android.graphics.Path().apply {
        val left = -3 * blur
        val right = realPeriod + 3 * blur
        moveTo(left, -100f)
        var x = left
        while (x <= right) {
            lineTo(x, edgeY(x) - lift)
            x += 3f
        }
        lineTo(right, -100f)
        close()
    }
    return softSprite(realPeriod, height, Offset.Zero) { c ->
        // Light catches the thin lower edge of each lump: paint the rim, then the band a little higher over it.
        c.drawPath(outline(0f), fillPaint(rim, blur = blur))
        val paint = fillPaint(top, blur = blur).apply {
            shader = LinearGradient(0f, 0f, 0f, depth + amp, top.toArgb(), edge.toArgb(), Shader.TileMode.CLAMP)
        }
        c.drawPath(outline(amp * 0.16f), paint)
    }
}

/** Clouds for every sky: soft cumulus, thin streaks, or a layered deck with ragged scud under it. */
internal class CloudLayer(private val s: Stage) {
    private val p = s.p
    private val u = s.u

    private val look: CloudLook = run {
        val lit = when {
            s.lowSun && !s.covered -> lerpColor(p.cloud, Color(0xFFFFCDA6), 0.55f)
            else -> p.cloud
        }
        val body = if (s.lowSun && !s.covered) lerpColor(lit, p.skyTop, 0.36f) else p.cloudShade
        val belly = if (s.lowSun && !s.covered) lerpColor(body, p.skyTop, 0.32f) else p.cloudBelly
        CloudLook(lit, body, belly)
    }

    /** Low clouds sit in the band between the hero text and the horizon and never rise into the text. */
    private val lowTop = (s.textBottom + 40f * u).coerceAtMost(s.ground - 30f * u)
    private val lowBottom = s.ground - 12f * u

    private class Placed(val sprite: Sprite, val x: Float, val y: Float, val parallax: Float, val alpha: Float)

    private fun placePuffs(
        count: Int,
        sizeBoost: Float,
        seedBase: Int,
        speedBoost: Float,
        tone: CloudLook,
        soft: Float = 1f,
        alpha: Float = 1f,
    ): List<Placed> =
        List(count) { i ->
            val seed = seedBase + i
            val y = lowTop + rnd(seed, 7) * (lowBottom - lowTop)
            // Clouds nearer the horizon are farther away: smaller, hazier, slower.
            val depth = ((y - lowTop) / max(1f, lowBottom - lowTop)).coerceIn(0f, 1f)
            val wanted = 210f * u * (0.75f + 0.5f * rnd(seed, 8)) * sizeBoost * (1f - 0.35f * depth)
            val room = (y - s.textBottom) / 0.72f
            val width = min(wanted, room).coerceAtLeast(60f * u)
            val sprite = cumulusSprite(seed, width, tone.toward(s.horizon, depth * 0.4f), s.light, soft)
            Placed(sprite, rnd(seed, 9), y, (0.55f + 0.45f * (1f - depth)) * speedBoost, alpha * (1f - 0.2f * depth))
        }.sortedByDescending { it.y } // farthest first, so nearer clouds pass in front

    private val puffs: List<Placed> = when (s.spec.sky) {
        SkyType.PartlyCloudy -> {
            val cover = s.spec.cloudCover.toFloat().coerceIn(0f, 100f)
            val count = (2 + cover / 100f * 5f).roundToInt().coerceIn(2, 7)
            placePuffs(count, 0.8f + cover / 250f, 0, 1f, look)
        }
        SkyType.Overcast, SkyType.Drizzle, SkyType.Rain, SkyType.Storm, SkyType.Snow ->
            // Ragged scud under the deck: soft, low contrast, moving faster than the deck above it.
            placePuffs(3, 0.6f, 20, 1.7f, CloudLook(lerpColor(p.cloudShade, p.cloud, 0.35f), p.cloudShade, p.cloudBelly), soft = 7f, alpha = 0.75f)
        else -> emptyList()
    }

    /** A mostly clear sky keeps two or three small broken streaks low over the horizon. */
    private val wisps: List<Placed> = if (s.spec.sky != SkyType.Clear || s.night) emptyList() else List(3) { i ->
        val y = lowTop + (0.1f + 0.33f * i) * (lowBottom - lowTop)
        // Lower means farther: smaller, hazier, fainter.
        val far = i / 2f
        val tone = look.toward(s.horizon, 0.15f + 0.3f * far)
        val width = (120f + 60f * rnd(i, 85)) * u * (1f - 0.3f * far)
        Placed(wispSprite(i + 3, width, tone, s.light), rnd(i, 86), y, 0.5f - 0.15f * far, 0.85f - 0.25f * far)
    }

    private class Band(val sprite: Sprite, val parallax: Float, val offset: Float)

    private val deck: List<Band> = if (!s.covered) emptyList() else {
        val storm = if (s.spec.sky == SkyType.Storm) 1.3f else 1f
        val period = s.w * 1.3f
        val rimLight = if (s.night || s.spec.sky == SkyType.Storm) 0.25f else 0.55f
        fun rim(c: Color) = lerpColor(c, lerpColor(p.cloud, Color.White, 0.4f), rimLight)
        val far = lerpColor(p.cloud, s.horizon, 0.4f)
        val mid = lerpColor(p.cloudShade, p.cloud, 0.6f)
        val near = lerpColor(p.cloudBelly, p.cloudShade, 0.75f)
        listOf(
            Band(deckSprite(31, period, 200f * u, 34f * u * storm, 5f * u, lerpColor(p.cloudShade, p.cloud, 0.5f), far, rim(far)), 0.45f, 0.13f),
            Band(deckSprite(37, period, 142f * u, 36f * u * storm, 4f * u, p.cloudShade, mid, rim(mid)), 0.7f, 0.51f),
            Band(deckSprite(43, period, 82f * u, 38f * u * storm, 3.5f * u, p.cloudBelly, near, rim(near)), 1f, 0.8f),
        )
    }

    /** Behind a deck on a day that isn't stormy, the sun still shows as a pale bright patch. */
    private val hiddenSun: Brush? = if (!s.covered || s.spec.time == TimeOfDay.Night || s.spec.sky == SkyType.Storm) null else {
        val a = if (s.spec.sky == SkyType.Rain) 0.08f else 0.16f
        Brush.radialGradient(
            0f to Color.White.copy(alpha = a),
            0.4f to Color.White.copy(alpha = a * 0.4f),
            1f to Color.White.copy(alpha = 0f),
            center = s.sunCenter,
            radius = 170f * u,
        )
    }

    /** The covered-sky deck, drawn under the hero veil. */
    fun DrawScope.drawDeck(t: Float) {
        for (band in deck) {
            val period = band.sprite.width
            var x = wrap(t * s.cloudSpeed * band.parallax + band.offset * period, period) - period
            while (x < size.width) {
                with(band.sprite) { draw(Offset(x, 0f)) }
                x += period
            }
        }
        hiddenSun?.let { drawRect(it) }
    }

    /** Low clouds, drawn over the hero veil: they stay below the text, so the veil would only grey them. */
    fun DrawScope.drawLow(t: Float) {
        for (c in wisps + puffs) {
            val span = size.width + c.sprite.width
            val x = wrap(c.x * span + t * s.cloudSpeed * c.parallax, span) - c.sprite.width / 2
            with(c.sprite) { draw(Offset(x, c.y), c.alpha) }
        }
    }

    private class Shaft(val sprite: Sprite, val x: Float, val parallax: Float)

    /** Soft curtains of rain hanging under the deck, slanted by the wind, drifting with the clouds. */
    private val shafts: List<Shaft> = run {
        val strength = when (s.spec.sky) {
            SkyType.Rain, SkyType.Storm -> (0.12f + s.spec.precipitation.toFloat() * 0.015f).coerceAtMost(0.22f)
            SkyType.Drizzle -> 0.07f
            else -> return@run emptyList()
        }
        val top = 150f * u
        val bottom = s.ground + 10f * u
        val slant = s.windEast * s.windFactor * 0.35f * (bottom - top)
        List(3) { i ->
            val width = (60f + 50f * rnd(i, 121)) * u
            val blur = 16f * u
            val pad = 3 * blur
            val left = pad + max(0f, -slant)
            val path = android.graphics.Path().apply {
                moveTo(left, pad)
                lineTo(left + width, pad)
                lineTo(left + width + slant, pad + bottom - top)
                lineTo(left + slant, pad + bottom - top)
                close()
            }
            val color = p.precipitation
            val sprite = softSprite(width + abs(slant) + 2 * pad, bottom - top + 2 * pad, Offset(left, pad - top)) { c ->
                c.drawPath(path, fillPaint(color, blur = blur).apply {
                    shader = LinearGradient(
                        0f, pad, 0f, pad + bottom - top,
                        intArrayOf(color.copy(alpha = 0f).toArgb(), color.copy(alpha = strength).toArgb(), color.copy(alpha = strength * 0.5f).toArgb()),
                        floatArrayOf(0f, 0.35f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                })
            }
            Shaft(sprite, rnd(i, 122), 0.8f + 0.3f * rnd(i, 123))
        }
    }

    fun DrawScope.drawRainShafts(t: Float) {
        for (shaft in shafts) {
            val span = size.width + shaft.sprite.width
            val x = wrap(shaft.x * span + t * s.cloudSpeed * shaft.parallax, span) - shaft.sprite.width * 0.5f
            with(shaft.sprite) { draw(Offset(x, 0f)) }
        }
    }

    private fun abs(v: Float) = kotlin.math.abs(v)
}
