package app.kweather.ui.home.cards

import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.ComfortMath
import app.kweather.domain.Weather
import app.kweather.ui.scene.LocalPalette

/**
 * The one card that only shows up when it matters (wind chill in the cold, heat index in the heat).
 * It always sits in the same slot, right after the details grid.
 */
@Composable
fun ComfortCard(weather: Weather) {
    val c = weather.current
    val f = LocalFmt.current
    val chill = ComfortMath.windChill(c.temperature, c.wind.speed)
    val heat = ComfortMath.heatIndex(c.temperature, c.humidity)
    when {
        chill != null && chill <= c.temperature - 2 -> {
            val minutes = ComfortMath.frostbiteMinutes(chill)
            Gauge(
                title = "Wind chill",
                trailing = if (minutes != null) "Frostbite risk: high" else "Dress for the wind",
                value = f.temp(chill),
                // -50 °C fills the bar.
                fraction = ((10 - chill) / 60).toFloat(),
                caption = if (minutes != null) "Exposed skin can freeze in under $minutes minutes."
                else "The wind makes it feel ${f.tempDelta(c.temperature - chill)} colder than the air.",
            )
        }
        heat != null && heat >= c.temperature + 1 -> Gauge(
            title = "Heat index",
            trailing = when {
                heat >= 41 -> "Danger"
                heat >= 32 -> "Extreme caution"
                else -> "Caution"
            },
            value = f.temp(heat),
            fraction = ((heat - 26) / 26).toFloat(),
            caption = "Humidity makes it feel ${f.tempDelta(heat - c.temperature)} hotter. Drink water and limit time in the sun.",
        )
        else -> Unit
    }
}

@Composable
private fun Gauge(title: String, trailing: String, value: String, fraction: Float, caption: String) {
    val p = LocalPalette.current
    SceneCard {
        CardTitle(title, trailing)
        Text(value, style = TextStyle(fontSize = 34.sp, color = p.onCard))
        Box(Modifier.fillMaxWidth().height(8.dp).background(p.cardHighlight, RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .background(p.accent, RoundedCornerShape(4.dp)),
            )
        }
        Text(caption, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
    }
}
