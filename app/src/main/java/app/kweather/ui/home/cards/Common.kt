package app.kweather.ui.home.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.Condition
import app.kweather.ui.scene.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun SceneCard(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.card,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (brush != null) Modifier.background(brush, CardShape) else Modifier.background(color, CardShape))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, trailing: String? = null) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCardVariant),
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
        }
    }
}

/** Small condition glyph drawn in code so it matches the scene art. */
@Composable
fun ConditionIcon(condition: Condition, isDay: Boolean, size: Dp = 24.dp) {
    val p = LocalPalette.current
    val ink = p.onCard
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        when {
            condition == Condition.Clear || condition == Condition.MostlyClear ->
                if (isDay) sun(Offset(12f * u, 12f * u), u, p.sunInk) else moon(Offset(12f * u, 12f * u), u, ink)
            condition == Condition.PartlyCloudy -> {
                if (isDay) sun(Offset(8.5f * u, 8.5f * u), u * 0.8f, p.sunInk) else moon(Offset(8.5f * u, 8.5f * u), u * 0.8f, ink)
                cloud(Offset(13.5f * u, 15.5f * u), u * 0.9f, ink, p.card)
            }
            condition == Condition.Fog -> repeat(3) { i ->
                drawLine(ink, Offset(4f * u, (8f + i * 4f) * u), Offset(20f * u, (8f + i * 4f) * u),
                    strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
            condition == Condition.Thunderstorm || condition == Condition.Hail -> {
                cloud(Offset(12f * u, 10f * u), u, ink, p.card)
                val bolt = Path().apply {
                    moveTo(13f * u, 14f * u); lineTo(10f * u, 19f * u); lineTo(12.5f * u, 19f * u)
                    lineTo(11f * u, 23f * u); lineTo(15.5f * u, 17.5f * u); lineTo(13f * u, 17.5f * u); close()
                }
                drawPath(bolt, p.sunInk)
            }
            condition.isSnowy -> {
                cloud(Offset(12f * u, 10f * u), u, ink, p.card)
                listOf(8f, 12f, 16f).forEach { x -> drawCircle(ink, 1.3f * u, Offset(x * u, 19f * u)) }
            }
            condition.isWet -> {
                cloud(Offset(12f * u, 10f * u), u, ink, p.card)
                listOf(8f, 12f, 16f).forEach { x ->
                    drawLine(p.rain, Offset(x * u, 16f * u), Offset((x - 1.2f) * u, 21f * u),
                        strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                }
            }
            else -> cloud(Offset(12f * u, 13f * u), u, ink, p.card)
        }
    }
}

private fun DrawScope.sun(c: Offset, u: Float, color: Color) {
    drawCircle(color, 4.5f * u, c)
    repeat(8) { i ->
        val a = i * PI / 4
        drawLine(
            color,
            c + Offset((cos(a) * 7 * u).toFloat(), (sin(a) * 7 * u).toFloat()),
            c + Offset((cos(a) * 9.5 * u).toFloat(), (sin(a) * 9.5 * u).toFloat()),
            strokeWidth = 1.8f * u, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(c: Offset, u: Float, ink: Color) {
    val path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c, 8f * u))
        val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(4f * u, -3f * u), 7f * u)) }
        op(this, cut, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(path, ink)
}

private fun DrawScope.cloud(c: Offset, u: Float, ink: Color, fill: Color) {
    fun oval(center: Offset, r: Float) = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    val base = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = c.x - 8f * u, top = c.y - 1f * u, right = c.x + 8f * u, bottom = c.y + 5f * u,
                radiusX = 3f * u, radiusY = 3f * u,
            ),
        )
    }
    // One merged outline, so no inner lines show where the puffs overlap.
    val merged = Path().apply {
        op(base, oval(c + Offset(-3.5f * u, 0.5f * u), 4.5f * u), androidx.compose.ui.graphics.PathOperation.Union)
    }
    val shape = Path().apply {
        op(merged, oval(c + Offset(2.5f * u, -1.5f * u), 5.5f * u), androidx.compose.ui.graphics.PathOperation.Union)
    }
    drawPath(shape, fill)
    drawPath(shape, ink, style = Stroke(width = 1.6f * u))
}

/** Compass-style arrow pointing where the wind is blowing to. */
fun DrawScope.windArrow(center: Offset, fromDegrees: Double, length: Float, color: Color) {
    val toward = Math.toRadians(fromDegrees + 180)
    val dx = sin(toward).toFloat()
    val dy = -cos(toward).toFloat()
    val tip = center + Offset(dx * length / 2, dy * length / 2)
    val tail = center - Offset(dx * length / 2, dy * length / 2)
    drawLine(color, tail, tip, strokeWidth = length * 0.14f, cap = StrokeCap.Round)
    val headSize = length * 0.38f
    val left = tip - Offset(dx * headSize, dy * headSize) + Offset(-dy * headSize * 0.6f, dx * headSize * 0.6f)
    val right = tip - Offset(dx * headSize, dy * headSize) - Offset(-dy * headSize * 0.6f, dx * headSize * 0.6f)
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, color)
}
