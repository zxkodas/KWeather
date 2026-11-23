package app.kweather.ui.home.cards

import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.domain.ComfortMath
import app.kweather.domain.Weather
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.theme.Figures
import app.kweather.ui.whole
import java.util.Locale

/** Always the same six tiles, in the same order, whatever the weather. */
@Composable
fun DetailsGrid(weather: Weather) {
    val c = weather.current
    val f = LocalFmt.current
    val pressureTrend = weather.hours.getOrNull(3)?.pressure?.let { later ->
        when {
            later - c.pressure > 1.0 -> "rising"
            c.pressure - later > 1.0 -> "falling"
            else -> "steady"
        }
    } ?: "steady"
    val uv = c.uvIndex ?: 0.0
    val aqi = weather.airQuality?.usAqi
    val rainNext24 = weather.hours.take(24).sumOf { it.precipitation }

    val tiles = listOf(
        Tile("Humidity", "${c.humidity.whole()}%", c.dewPoint?.let { "Dew point ${f.temp(it)}" } ?: ""),
        Tile("Pressure", f.pressureValue(c.pressure), "${f.pressureUnit} · $pressureTrend"),
        Tile("UV index", uv.whole(), ComfortMath.uvLabel(uv)),
        Tile("Air quality", aqi?.toString() ?: "–", aqi?.let { ComfortMath.aqiLabel(it) } ?: "Unavailable"),
        Tile(
            "Visibility",
            c.visibility?.let(f::distance) ?: "–",
            c.visibility?.let { if (it >= 10_000) "Clear" else if (it >= 4_000) "Hazy" else "Poor" } ?: "",
        ),
        Tile("Rain next 24 h", f.precip(rainNext24), "${f.precip(c.precipitation)} right now"),
    )

    // One panel with dividers rather than six separate boxes: these are supporting figures.
    val p = LocalPalette.current
    val line = p.onCardVariant.copy(alpha = 0.2f)
    SceneCard {
        tiles.chunked(2).forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = line)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TileView(row[0], Modifier.weight(1f))
                VerticalDivider(color = line)
                TileView(row[1], Modifier.weight(1f))
            }
        }
    }
}

private data class Tile(val label: String, val value: String, val caption: String)

@Composable
private fun TileView(tile: Tile, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tile.label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCardVariant))
        Text(tile.value, style = Figures.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = p.onCard))
        Text(tile.caption, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
    }
}
