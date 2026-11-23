package app.kweather.ui.home.cards

import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.Day
import app.kweather.domain.Weather
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.shortDay
import app.kweather.ui.whole

private const val DAYS = 10

@Composable
fun DailyCard(weather: Weather) {
    val p = LocalPalette.current
    val days = weather.days.take(DAYS)
    if (days.isEmpty()) return
    val lowest = days.minOf { it.min }
    val highest = days.maxOf { it.max }

    SceneCard {
        CardTitle("$DAYS-day forecast", "rain · max gust (${LocalFmt.current.windUnit})")
        days.forEachIndexed { i, day ->
            if (i > 0) HorizontalDivider(color = p.onCardVariant.copy(alpha = 0.15f))
            DayRow(day, if (i == 0) "Today" else day.date.shortDay(), lowest, highest)
        }
    }
}

@Composable
private fun DayRow(day: Day, label: String, lowest: Double, highest: Double) {
    val p = LocalPalette.current
    val f = LocalFmt.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = if (label == "Today") FontWeight.Bold else FontWeight.Normal, color = p.onCard), modifier = Modifier.width(48.dp))
        ConditionIcon(day.condition, isDay = true, size = 22.dp)
        Text(
            if (day.precipitationProbability >= 10) "${day.precipitationProbability}%" else "",
            style = TextStyle(fontSize = 12.sp, color = p.rain),
            modifier = Modifier.width(34.dp),
        )
        Text(
            f.windValue(day.gustMax),
            style = TextStyle(fontSize = 12.sp, color = if (day.gustMax >= 50) p.gust else p.onCardVariant),
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End,
        )
        Text(f.temp(day.min), style = TextStyle(fontSize = 14.sp, color = p.onCardVariant), modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        RangeBar(day, lowest, highest, Modifier.weight(1f).height(6.dp))
        Text(f.temp(day.max), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCard), modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun RangeBar(day: Day, lowest: Double, highest: Double, modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val span = (highest - lowest).coerceAtLeast(1.0)
        val start = ((day.min - lowest) / span * size.width).toFloat()
        val end = ((day.max - lowest) / span * size.width).toFloat()
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.cardHighlight, cornerRadius = r)
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(day.min), tempColor(day.max)), startX = start, endX = end),
            topLeft = Offset(start, 0f),
            size = Size((end - start).coerceAtLeast(size.height), size.height),
            cornerRadius = r,
        )
    }
}

/** Cold blue to hot orange-red. */
private fun tempColor(t: Double): Color = when {
    t <= -10 -> Color(0xFF7FA8E0)
    t <= 0 -> Color(0xFF8EC9E8)
    t <= 10 -> Color(0xFF7FD1B9)
    t <= 18 -> Color(0xFFD9C890)
    t <= 25 -> Color(0xFFF0B060)
    t <= 32 -> Color(0xFFF08A50)
    else -> Color(0xFFE85A4F)
}
