package app.kweather.ui.alerts

import app.kweather.ui.settings.Fmt
import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.R
import app.kweather.domain.Alert
import app.kweather.domain.AlertLevel
import app.kweather.domain.eventLabel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Container, strong accent and text color for each level. Text contrast checked against the container. */
data class LevelColors(val container: Color, val accent: Color, val onContainer: Color, val onContainerVariant: Color)

fun AlertLevel.colors(): LevelColors = when (this) {
    AlertLevel.Yellow -> LevelColors(Color(0xFFFFE08A), Color(0xFFE5A91A), Color(0xFF2B1D00), Color(0xFF5A4300))
    AlertLevel.Orange -> LevelColors(Color(0xFFFFC08A), Color(0xFFE5701A), Color(0xFF331400), Color(0xFF6A3200))
    AlertLevel.Red -> LevelColors(Color(0xFFFFB4AB), Color(0xFFD93025), Color(0xFF410002), Color(0xFF7A1010))
}

/** "Now, until Tue 14:59" or "From Tue 03:00 to Wed 14:59". */
fun Alert.timing(f: Fmt, now: OffsetDateTime = OffsetDateTime.now()): String {
    val end = expires?.let { "until ${f.dayTime(it)}" }
    return when {
        onset != null && onset.isAfter(now) ->
            "From ${f.dayTime(onset)}" + (expires?.let { " to ${f.dayTime(it)}" } ?: "")
        end != null -> "Now, $end"
        else -> "Now"
    }
}

fun Alert.title(): String = "${level.label} alert · ${eventLabel()}"

@Composable
fun AlertBanner(alert: Alert, onClick: () -> Unit) {
    val c = alert.level.colors()
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.container, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = c.onContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(alert.title(), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onContainer))
            Text(
                "${alert.source} · ${alert.timing(LocalFmt.current)}",
                style = TextStyle(fontSize = 13.sp, color = c.onContainerVariant),
            )
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Open alert details", tint = c.onContainerVariant)
    }
}
