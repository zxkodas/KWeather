package app.kweather.ui.alerts

import app.kweather.ui.settings.LocalFmt
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.R
import app.kweather.data.alerts.SmnAlertProvider
import app.kweather.domain.Alert
import app.kweather.ui.demo.DemoData
import app.kweather.ui.home.StatusBarIcons

@Composable
fun AlertScreen(alert: Alert, placeName: String, onBack: () -> Unit) {
    val c = alert.level.colors()
    val context = LocalContext.current
    StatusBarIcons(dark = true)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.container)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = c.onContainer)
            }
            Text(placeName, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = c.onContainer))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(alert.title(), style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = c.onContainer))
            Text(alert.timing(LocalFmt.current), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.onContainer))
            if (alert.headline.isNotBlank() && !alert.headline.equals(alert.event, ignoreCase = true)) {
                Text(alert.headline.sentenceCase(), style = TextStyle(fontSize = 16.sp, color = c.onContainer))
            }
            if (alert.areaDescription.isNotBlank()) {
                Section("Area", c) { Body(alert.areaDescription.sentenceCase(), c) }
            }
            if (alert.description.isNotBlank()) {
                Section("What to expect", c) { Body(alert.description, c) }
            }
            alert.instruction?.let { instruction ->
                Section("What to do", c) {
                    splitSteps(instruction).forEach { step -> Body("•  $step", c) }
                }
            }
            val demo = alert.source == DemoData.SOURCE
            Text(
                if (demo) "Example alert for previewing the layout. Not a real warning."
                else "Official text from the ${alert.sourceName}, in Spanish. Licensed CC BY 4.0.",
                style = TextStyle(fontSize = 13.sp, color = c.onContainerVariant),
            )
            if (!demo) OutlinedButton(onClick = {
                val url = if (alert.isShortTerm) SmnAlertProvider.ALERTS_PAGE else alert.web ?: SmnAlertProvider.ALERTS_PAGE
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) {
                Text("Open smn.gob.ar/alertas", color = c.onContainer)
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Section(title: String, c: LevelColors, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.accent.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onContainerVariant))
        content()
    }
}

@Composable
private fun Body(text: String, c: LevelColors) {
    Text(text, style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = c.onContainer))
}

/** SMN numbers its steps inline ("1- Evitá salir. 2- ..."); otherwise split on sentences. */
internal fun splitSteps(text: String): List<String> {
    val numbered = text.split(Regex("\\s*\\d+\\s*[-.)]\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    if (numbered.size > 1) return numbered
    return text.split(Regex("(?<=\\.)\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
}

/** SMN short-term warnings arrive in all caps. */
private fun String.sentenceCase(): String {
    val letters = filter { it.isLetter() }
    if (letters.isEmpty() || letters.any { it.isLowerCase() }) return this
    return lowercase().replaceFirstChar { it.titlecase() }
}
