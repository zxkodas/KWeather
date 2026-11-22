package app.kweather.ui.home.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.Weather
import app.kweather.domain.WindMath
import app.kweather.ui.hourLabel
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.whole
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WindCard(weather: Weather) {
    val p = LocalPalette.current
    val wind = weather.current.wind
    val force = WindMath.beaufort(wind.speed)
    val point = WindMath.compassPoint(wind.direction)
    val upper = weather.hours.firstOrNull()

    SceneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Wind",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCardVariant),
                modifier = Modifier.weight(1f),
            )
            Text(
                "Beaufort $force · ${WindMath.beaufortLabel(force).lowercase()}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.onCard),
                modifier = Modifier
                    .background(p.cardHighlight, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Compass(wind.direction, Modifier.size(124.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Stat("Speed", wind.speed.whole(), "km/h", Modifier.weight(1f)); Stat("Gusts", wind.gusts.whole(), "km/h", Modifier.weight(1f), p.gust) }
                Row {
                    Stat("From", point, "${wind.direction.whole()}°", Modifier.weight(1f))
                    val high = upper?.windSpeed120m ?: upper?.windSpeed80m
                    val label = if (upper?.windSpeed120m != null) "At 120 m" else "At 80 m"
                    Stat(label, high?.whole() ?: "–", "km/h", Modifier.weight(1f))
                }
            }
        }
        GustBars(weather)
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, style = TextStyle(fontSize = 12.sp, color = p.onCardVariant))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 22.sp, color = valueColor ?: p.onCard)) { append(value) }
                withStyle(SpanStyle(fontSize = 12.sp, color = p.onCardVariant)) { append(" $unit") }
            },
        )
    }
}

@Composable
private fun Compass(fromDegrees: Double, modifier: Modifier) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(p.cardHighlight, r, c)
        repeat(16) { i ->
            val a = Math.toRadians(i * 22.5)
            val inner = if (i % 4 == 0) r * 0.80f else r * 0.86f
            drawLine(
                p.onCardVariant.copy(alpha = 0.6f),
                c + Offset((sin(a) * inner).toFloat(), (-cos(a) * inner).toFloat()),
                c + Offset((sin(a) * r * 0.92f).toFloat(), (-cos(a) * r * 0.92f).toFloat()),
                strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.onCardVariant)
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, deg) ->
            val a = Math.toRadians(deg)
            val text = measurer.measure(label, style)
            val pos = c + Offset((sin(a) * r * 0.62f).toFloat(), (-cos(a) * r * 0.62f).toFloat())
            drawText(text, topLeft = pos - Offset(text.size.width / 2f, text.size.height / 2f))
        }
        windArrow(c, fromDegrees, r * 1.05f, p.accent)
        drawCircle(p.card, 4.dp.toPx(), c)
    }
}

/** Speed bars with gust ticks for the next 12 hours. */
@Composable
private fun GustBars(weather: Weather) {
    val p = LocalPalette.current
    val hours = weather.hours.take(12)
    if (hours.isEmpty()) return
    val peak = hours.maxBy { it.wind.gusts }
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("Next 12 h · speed and gusts", style = TextStyle(fontSize = 12.sp, color = p.onCardVariant), modifier = Modifier.weight(1f))
            Text("Peak ${peak.wind.gusts.whole()} at ${peak.time.hourLabel()}:00", style = TextStyle(fontSize = 12.sp, color = p.onCardVariant))
        }
        Canvas(Modifier.fillMaxWidth().height(78.dp)) {
            val maxV = hours.maxOf { it.wind.gusts }.coerceAtLeast(10.0)
            val labelSpace = 16.dp.toPx()
            val chartH = size.height - labelSpace
            val slot = size.width / hours.size
            val barW = slot * 0.62f
            val style = TextStyle(fontSize = 10.sp, color = p.onCardVariant)
            hours.forEachIndexed { i, h ->
                val x = slot * i + (slot - barW) / 2
                val bh = (h.wind.speed / maxV * chartH).toFloat()
                drawRoundRect(p.accent.copy(alpha = 0.35f), Offset(x, chartH - bh), Size(barW, bh), CornerRadius(3.dp.toPx()))
                val gy = (chartH - h.wind.gusts / maxV * chartH).toFloat()
                drawLine(p.gust, Offset(x, gy), Offset(x + barW, gy), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                if (i % 3 == 0) {
                    val t = measurer.measure(h.time.hourLabel(), style)
                    drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2, size.height - t.size.height))
                }
            }
        }
    }
}
