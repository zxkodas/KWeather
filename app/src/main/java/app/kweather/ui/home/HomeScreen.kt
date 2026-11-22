package app.kweather.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kweather.domain.Weather
import app.kweather.domain.labelFor
import app.kweather.ui.deg
import app.kweather.ui.hhmm
import app.kweather.ui.home.cards.ComfortCard
import app.kweather.ui.home.cards.DailyCard
import app.kweather.ui.home.cards.DetailsGrid
import app.kweather.ui.home.cards.HourlyCard
import app.kweather.ui.home.cards.SunMoonCard
import app.kweather.ui.home.cards.WindCard
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.scene.SkyType
import app.kweather.ui.scene.TimeOfDay
import app.kweather.ui.scene.WeatherScene
import app.kweather.ui.theme.KWeatherTheme

@Composable
fun HomeRoute(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val weather = state.weather
    if (weather == null) {
        LoadingScreen(error = state.error, onRetry = vm::refresh)
        return
    }
    val spec = remember(weather) { SceneSpec.from(weather) }
    val palette = remember(spec) { ScenePalette.forSpec(spec) }
    KWeatherTheme(palette) {
        HomeScreen(weather, spec, palette, state.refreshing, state.error, onRefresh = vm::refresh)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    weather: Weather,
    spec: SceneSpec,
    palette: ScenePalette,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
    val heroPx = with(density) { HERO_HEIGHT.toPx() }
    // 0 while the status bar is over the sky, 1 once cards scroll under it.
    val scrim by remember(statusBarPx, heroPx) {
        derivedStateOf {
            ((scroll.value - (heroPx - 2 * statusBarPx)) / statusBarPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
    }
    val activity = androidx.activity.compose.LocalActivity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val darkIcons = !palette.isDark && scrim > 0.5f
    androidx.compose.runtime.SideEffect {
        activity?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
                WeatherScene(spec, palette, Modifier.fillMaxSize())
                Hero(weather, Modifier.statusBarsPadding().padding(start = 24.dp, top = 56.dp))
            }
            Column(
                Modifier.padding(horizontal = 16.dp).offset(y = (-8).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) ErrorBanner(error)
                HourlyCard(weather)
                WindCard(weather)
                DetailsGrid(weather)
                ComfortCard(weather)
                SunMoonCard(weather)
                DailyCard(weather)
                Text(
                    "Updated ${weather.fetchedAt.hhmm()} · Forecast data: Open-Meteo.com",
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

private val HERO_HEIGHT = 480.dp

private val heroShadow = Shadow(color = Color(0x40000000), blurRadius = 12f)

@Composable
private fun Hero(weather: Weather, modifier: Modifier = Modifier) {
    val c = weather.current
    val today = weather.today
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            weather.place.name,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = heroShadow),
        )
        Text(
            c.temperature.deg(),
            style = TextStyle(fontSize = 104.sp, fontWeight = FontWeight.Light, color = Color.White,
                letterSpacing = (-4).sp, shadow = heroShadow),
        )
        Text(
            "${c.condition.labelFor(c.isDay)} · feels ${c.feelsLike.deg()}",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = heroShadow),
        )
        if (today != null) {
            Text(
                "H ${today.max.deg()}  L ${today.min.deg()}",
                style = TextStyle(fontSize = 15.sp, color = Color(0xFFE6EEF7), shadow = heroShadow),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val p = app.kweather.ui.scene.LocalPalette.current
    Text(
        message,
        style = TextStyle(fontSize = 14.sp, color = p.onCard),
        modifier = Modifier
            .fillMaxWidth()
            .background(p.card, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun LoadingScreen(error: String?, onRetry: () -> Unit) {
    val palette = remember {
        ScenePalette.forSpec(SceneSpec(SkyType.Clear, TimeOfDay.Day, 0.0, 0.0, 0.0, 0.0, false))
    }
    Box(Modifier.fillMaxSize().background(palette.skyTop), contentAlignment = Alignment.Center) {
        if (error == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(error, style = TextStyle(fontSize = 16.sp, color = Color.White))
                androidx.compose.material3.OutlinedButton(onClick = onRetry) {
                    Text("Try again", color = Color.White)
                }
            }
        }
    }
}
