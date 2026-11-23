package app.kweather.ui.home

import app.kweather.ui.settings.LocalFmt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.kweather.R
import app.kweather.domain.Weather
import app.kweather.domain.labelFor
import app.kweather.ui.alerts.AlertBanner
import app.kweather.ui.home.cards.ComfortCard
import app.kweather.ui.home.cards.DailyCard
import app.kweather.ui.home.cards.DetailsGrid
import app.kweather.ui.home.cards.HourlyCard
import app.kweather.ui.home.cards.SunMoonCard
import app.kweather.ui.home.cards.WindCard
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.scene.WeatherScene
import app.kweather.ui.theme.KWeatherTheme

/** Actions a page can trigger, so the page itself stays free of navigation and permissions. */
class PageActions(
    val onRefresh: (PageState) -> Unit,
    val onOpenPlaces: () -> Unit,
    val onRequestLocation: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenLocationSettings: () -> Unit,
    val onOpenAlert: (pageKey: String, alertId: String) -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun WeatherPage(page: PageState, index: Int, count: Int, actions: PageActions) {
    val weather = page.weather
    if (weather == null) {
        PageMessage(page, index, count, actions)
        return
    }
    val spec = remember(weather) { SceneSpec.from(weather) }
    val palette = remember(spec) { ScenePalette.forSpec(spec) }
    KWeatherTheme(palette) {
        WeatherContent(page, weather, spec, palette, index, count, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(
    page: PageState,
    weather: Weather,
    spec: SceneSpec,
    palette: ScenePalette,
    index: Int,
    count: Int,
    actions: PageActions,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
    val heroPx = with(density) { HERO_HEIGHT.toPx() }
    // 0 while the status bar is over the sky, 1 once cards scroll under it.
    val scrim by remember(statusBarPx, heroPx) {
        derivedStateOf {
            ((scroll.value - (heroPx - 2 * statusBarPx)) / statusBarPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
    }
    StatusBarIcons(dark = !palette.isDark && scrim > 0.5f)

    PullToRefreshBox(
        isRefreshing = page.refreshing,
        onRefresh = { actions.onRefresh(page) },
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
                WeatherScene(spec, palette, Modifier.fillMaxSize())
                Column(Modifier.statusBarsPadding()) {
                    PageHeader(index, count, actions.onOpenPlaces, actions.onOpenSettings)
                    Hero(page, weather, Modifier.padding(start = 24.dp, top = 8.dp))
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp).offset(y = (-8).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                page.alerts.take(3).forEach { alert ->
                    AlertBanner(alert) { actions.onOpenAlert(page.key, alert.id) }
                }
                if (page.alerts.size > 3) Banner("${page.alerts.size - 3} more alerts for this place.")
                page.locationIssue?.let { Banner("${it.message} Showing your last known location.") }
                page.error?.let { Banner(it) }
                HourlyCard(weather)
                WindCard(weather)
                DetailsGrid(weather)
                ComfortCard(weather)
                SunMoonCard(weather)
                DailyCard(weather)
                Text(
                    if (page.key.startsWith("demo:")) "Demo data, not a real forecast" else "Updated ${LocalFmt.current.time(weather.fetchedAt)} · Forecast data: Open-Meteo.com",
                    style = TextStyle(fontSize = 12.sp, color = palette.onCardVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .graphicsLayer { alpha = scrim }
                .background(palette.background),
        )
    }
}

@Composable
fun StatusBarIcons(dark: Boolean) {
    val activity = androidx.activity.compose.LocalActivity.current
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = dark }
    }
}

/** Places button on the left, one dot per page in the middle, settings on the right. */
@Composable
private fun PageHeader(index: Int, count: Int, onOpenPlaces: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        IconButton(
            onClick = onOpenPlaces,
            modifier = Modifier.align(Alignment.CenterStart),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x33000000), contentColor = Color.White),
        ) {
            Icon(painterResource(R.drawable.ic_places), contentDescription = "Places")
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x33000000), contentColor = Color.White),
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
        }
        if (count > 1) {
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == index) 18.dp else 6.dp)
                            .background(if (i == index) Color.White else Color(0x80FFFFFF), CircleShape),
                    )
                }
            }
        }
    }
}

private val HERO_HEIGHT = 480.dp

private val heroShadow = Shadow(color = Color(0x40000000), blurRadius = 12f)

@Composable
private fun Hero(page: PageState, weather: Weather, modifier: Modifier = Modifier) {
    val c = weather.current
    val today = weather.today
    val f = LocalFmt.current
    val name = page.place?.name ?: weather.place.name
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (page.isCurrent) {
                Icon(
                    painterResource(R.drawable.ic_location),
                    contentDescription = "Current location",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(name, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = heroShadow))
        }
        Text(
            f.temp(c.temperature),
            style = TextStyle(fontSize = 104.sp, fontWeight = FontWeight.Light, color = Color.White,
                letterSpacing = (-4).sp, shadow = heroShadow),
        )
        Text(
            "${c.condition.labelFor(c.isDay)} · feels ${f.temp(c.feelsLike)}",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = heroShadow),
        )
        if (today != null) {
            Text(
                "H ${f.temp(today.max)}  L ${f.temp(today.min)}",
                style = TextStyle(fontSize = 15.sp, color = Color(0xFFE6EEF7), shadow = heroShadow),
            )
        }
    }
}

@Composable
private fun Banner(message: String) {
    val p = LocalPalette.current
    Text(
        message,
        style = TextStyle(fontSize = 14.sp, color = p.onCard),
        modifier = Modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Loading, error and location-problem states for a page that has no forecast yet. */
@Composable
private fun PageMessage(page: PageState, index: Int, count: Int, actions: PageActions) {
    val palette = ScenePalette.default
    StatusBarIcons(dark = false)
    Box(Modifier.fillMaxSize().background(palette.skyTop)) {
        Column(Modifier.statusBarsPadding()) { PageHeader(index, count, actions.onOpenPlaces, actions.onOpenSettings) }
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val issue = page.locationIssue
            val title = page.place?.name ?: if (page.isCurrent) "Current location" else ""
            Text(title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White))
            when {
                page.isCurrent && page.place == null && issue != null -> {
                    Message(issue.message)
                    when (issue) {
                        LocationIssue.NoPermission -> {
                            WhiteButton("Allow location", actions.onRequestLocation)
                            WhiteButton("Open app settings", actions.onOpenAppSettings)
                        }
                        LocationIssue.LocationOff -> WhiteButton("Open location settings", actions.onOpenLocationSettings)
                        LocationIssue.Unavailable -> WhiteButton("Try again") { actions.onRefresh(page) }
                    }
                }
                page.refreshing || (page.error == null && issue == null) -> {
                    CircularProgressIndicator(color = Color.White)
                    Message(if (page.isCurrent && page.place == null) "Finding your location…" else "Loading the forecast…")
                }
                else -> {
                    Message(page.error ?: issue?.message ?: "Something went wrong.")
                    WhiteButton("Try again") { actions.onRefresh(page) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center))
}

@Composable
private fun WhiteButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, color = Color.White) }
}
