package app.kweather.ui.home.cards

import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.MoonMath
import app.kweather.domain.Weather
import app.kweather.ui.durationHm
import app.kweather.ui.scene.LocalPalette
import java.time.Duration
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun SunMoonCard(weather: Weather) {
    val p = LocalPalette.current
    val today = weather.today ?: return
    val now = weather.current.time
    val polar = today.daylight <= 0.0
    // Open-Meteo reports 00:00 for both during polar night; hide them.
    val sunrise = today.sunrise.takeUnless { polar }
    val sunset = today.sunset.takeUnless { polar }
    val progress = if (sunrise != null && sunset != null && !polar) {
        val total = Duration.between(sunrise, sunset).seconds.toFloat()
        (Duration.between(sunrise, now).seconds / total)
    } else {
        -1f
    }
    val phase = MoonMath.phase(now.toInstant(ZoneOffset.UTC))
    val f = LocalFmt.current

    SceneCard {
        CardTitle(
            "Sun and moon",
            when {
                polar -> "Polar night"
                today.daylight >= 86_000 -> "Midnight sun"
                else -> "${today.daylight.durationHm()} of daylight"
            },
        )
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val w = size.width
            val h = size.height
            val p0 = Offset(0f, h)
            val p1 = Offset(w / 2, -h * 0.85f)
            val p2 = Offset(w, h)
            fun at(t: Float): Offset {
                val a = (1 - t) * (1 - t)
                val b = 2 * (1 - t) * t
                val c = t * t
                return Offset(a * p0.x + b * p1.x + c * p2.x, a * p0.y + b * p1.y + c * p2.y)
            }
            val full = Path().apply { moveTo(p0.x, p0.y); quadraticTo(p1.x, p1.y, p2.x, p2.y) }
            drawPath(full, p.onCardVariant.copy(alpha = 0.5f),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            drawLine(p.onCardVariant.copy(alpha = 0.4f), Offset(0f, h - 1), Offset(w, h - 1), strokeWidth = 1.dp.toPx())
            if (progress in 0f..1f) {
                val done = Path().apply {
                    moveTo(p0.x, p0.y)
                    var t = 0f
                    while (t <= progress) { val pt = at(t); lineTo(pt.x, pt.y); t += 0.02f }
                }
                drawPath(done, p.sunInk, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(p.sunInk, 7.dp.toPx(), at(progress))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Sunrise ${sunrise?.let(f::time) ?: "–"}", style = TextStyle(fontSize = 14.sp, color = p.onCard), modifier = Modifier.weight(1f))
            Text("Sunset ${sunset?.let(f::time) ?: "–"}", style = TextStyle(fontSize = 14.sp, color = p.onCard))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MoonGlyph(phase, southern = weather.place.latitude < 0, Modifier.size(48.dp))
            Column {
                Text(
                    "${MoonMath.name(phase)} · ${(MoonMath.illumination(phase) * 100).roundToInt()}%",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = p.onCard),
                )
                val days = MoonMath.daysToFull(phase)
                Text(
                    when (days) {
                        0 -> "Full moon tonight"
                        1 -> "Full moon tomorrow"
                        else -> "Full moon in $days days"
                    },
                    style = TextStyle(fontSize = 13.sp, color = p.onCardVariant),
                )
            }
        }
    }
}

@Composable
private fun MoonGlyph(phase: Double, southern: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val r = size.minDimension / 2
        val disc = Path().apply { addOval(Rect(center, r)) }
        clipPath(disc) {
            // Dark side reads on both light and dark cards.
            drawCircle(androidx.compose.ui.graphics.lerp(p.card, p.onCard, 0.35f), r, center)
            val lit = Color(0xFFF7EBC0)
            // Lit part: full disc minus a shifted shadow disc. Northern view: waxing = right side lit.
            val north = if (phase <= 0.5) -(phase * 4 * r).toFloat() else ((1 - phase) * 4 * r).toFloat()
            val shift = if (southern) -north else north
            val litPath = Path().apply {
                addOval(Rect(center, r))
                val shadow = Path().apply { addOval(Rect(center + Offset(shift, 0f), r * 1.02f)) }
                op(this, shadow, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            drawPath(litPath, lit)
        }
    }
}
