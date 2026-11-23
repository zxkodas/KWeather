package app.kweather.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.BuildConfig
import app.kweather.R
import app.kweather.ui.home.StatusBarIcons
import app.kweather.ui.scene.LocalPalette

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenDemo: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    StatusBarIcons(dark = !p.isDark)
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = p.onCard)
            }
            Text("Settings", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = p.onCard))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section("Units") {
                Choice("Temperature", TempUnit.entries, settings.temperature, { it.label }) { v -> onChange { it.copy(temperature = v) } }
                Choice("Wind speed", WindUnit.entries, settings.wind, { it.label }) { v -> onChange { it.copy(wind = v) } }
                Choice("Pressure", PressureUnit.entries, settings.pressure, { it.label }) { v -> onChange { it.copy(pressure = v) } }
                Choice("Rain", PrecipUnit.entries, settings.precipitation, { it.label }) { v -> onChange { it.copy(precipitation = v) } }
                Choice("Distance", DistanceUnit.entries, settings.distance, { it.label }) { v -> onChange { it.copy(distance = v) } }
            }
            Section("Display") {
                Toggle("24-hour clock", if (settings.clock24h) "15:00" else "3:00 PM", settings.clock24h) { v ->
                    onChange { it.copy(clock24h = v) }
                }
                Toggle("Animated scenes", "Rain, snow, clouds and stars move with the wind", settings.animations) { v ->
                    onChange { it.copy(animations = v) }
                }
            }
            Section("Developer") {
                Link("Demo gallery", "Example cities showing every scene, alert and card", onOpenDemo)
            }
            Section("About") {
                Info("KWeather", "Version ${BuildConfig.VERSION_NAME}")
                Info("Forecast, air quality and city search", "Open-Meteo.com, CC BY 4.0")
                Info("Weather alerts for Argentina", "Servicio Meteorológico Nacional (SMN), CC BY 4.0")
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCardVariant), modifier = Modifier.padding(start = 4.dp))
        Column(
            Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(24.dp)).padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TextStyle(fontSize = 16.sp, color = p.onCard))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = p.accent,
                        activeContentColor = p.background,
                        inactiveContainerColor = p.card,
                        inactiveContentColor = p.onCard,
                        activeBorderColor = p.accent,
                        inactiveBorderColor = p.onCardVariant.copy(alpha = 0.4f),
                    ),
                    icon = {},
                ) { Text(text(option)) }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 16.sp, color = p.onCard))
            Text(caption, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun Link(label: String, caption: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 16.sp, color = p.onCard))
            Text(caption, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = p.onCardVariant)
    }
}

@Composable
private fun Info(label: String, value: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, style = TextStyle(fontSize = 16.sp, color = p.onCard))
        Text(value, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
    }
}
