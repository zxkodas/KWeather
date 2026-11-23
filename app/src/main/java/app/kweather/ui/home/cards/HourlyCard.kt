package app.kweather.ui.home.cards

import app.kweather.ui.settings.Fmt
import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.Weather
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.whole

private const val HOURS = 24
private val COLUMN = 56.dp

@Composable
fun HourlyCard(weather: Weather) {
    val p = LocalPalette.current
    val hours = weather.hours.take(HOURS)
    if (hours.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val f = LocalFmt.current

    SceneCard {
        CardTitle("Next 24 hours")
        Text(summary(weather, f), style = TextStyle(fontSize = 15.sp, color = p.onCard))
        Canvas(
            Modifier
                .horizontalScroll(rememberScrollState())
                .width(COLUMN * hours.size)
                .height(196.dp),
        ) {
            val col = COLUMN.toPx()
            val minT = hours.minOf { it.temperature }
            val maxT = hours.maxOf { it.temperature }
            val range = (maxT - minT).coerceAtLeast(1.0)
            val tempTop = 26.dp.toPx()
            val tempBottom = 84.dp.toPx()
            fun x(i: Int) = col * i + col / 2
            fun y(t: Double) = (tempBottom - (t - minT) / range * (tempBottom - tempTop)).toFloat()

            // Temperature line.
            val line = Path()
            hours.forEachIndexed { i, h ->
                if (i == 0) line.moveTo(x(i), y(h.temperature)) else line.lineTo(x(i), y(h.temperature))
            }
            drawPath(line, p.onCard, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

            val labelStyle = TextStyle(fontSize = 13.sp, color = p.onCard, fontWeight = FontWeight.Medium)
            val smallStyle = TextStyle(fontSize = 11.sp, color = p.onCardVariant)
            val rainStyle = TextStyle(fontSize = 11.sp, color = p.rain)
            val barBottom = 132.dp.toPx()
            val barMax = 34.dp.toPx()

            hours.forEachIndexed { i, h ->
                val cx = x(i)
                drawCircle(p.onCard, 3.dp.toPx(), Offset(cx, y(h.temperature)))
                val temp = measurer.measure(f.temp(h.temperature), labelStyle)
                drawText(temp, topLeft = Offset(cx - temp.size.width / 2, y(h.temperature) - temp.size.height - 4.dp.toPx()))

                // Rain chance bar.
                if (h.precipitationProbability > 0) {
                    val bh = barMax * h.precipitationProbability / 100f
                    drawRoundRect(
                        p.rain.copy(alpha = 0.55f),
                        topLeft = Offset(cx - 9.dp.toPx(), barBottom - bh),
                        size = Size(18.dp.toPx(), bh),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                if (h.precipitationProbability >= 20) {
                    val pct = measurer.measure("${h.precipitationProbability}%", rainStyle)
                    drawText(pct, topLeft = Offset(cx - pct.size.width / 2, barBottom - barMax - pct.size.height))
                }

                // Wind arrow + speed.
                windArrow(Offset(cx, 150.dp.toPx()), h.wind.direction, 14.dp.toPx(), p.onCard)
                val wind = measurer.measure(f.windValue(h.wind.speed), smallStyle)
                drawText(wind, topLeft = Offset(cx - wind.size.width / 2, 160.dp.toPx()))

                val label = if (i == 0) "Now" else f.hour(h.time)
                val time = measurer.measure(label, smallStyle.copy(fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal))
                drawText(time, topLeft = Offset(cx - time.size.width / 2, 178.dp.toPx()))
            }
        }
        Text(
            "Bars: chance of rain · arrows: wind direction and speed (${f.windUnit})",
            style = TextStyle(fontSize = 12.sp, color = p.onCardVariant),
        )
    }
}

/** One plain sentence about the next 12 hours. */
private fun summary(weather: Weather, f: Fmt): String {
    val next = weather.hours.take(12)
    val wetStart = next.firstOrNull { it.precipitationProbability >= 50 }
    val peakGust = next.maxByOrNull { it.wind.gusts }
    val parts = mutableListOf<String>()
    parts += if (wetStart != null) {
        val label = if (wetStart == next.first()) "now" else "from ${f.time(wetStart.time)}"
        "${wetStart.condition.label} likely $label"
    } else {
        "No rain expected in the next 12 hours"
    }
    if (peakGust != null && peakGust.wind.gusts >= 40) {
        parts += "gusts up to ${f.wind(peakGust.wind.gusts)} around ${f.time(peakGust.time)}"
    }
    return parts.joinToString(", ") + "."
}
