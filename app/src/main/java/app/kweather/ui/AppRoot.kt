package app.kweather.ui

import app.kweather.ui.settings.SettingsViewModel
import app.kweather.ui.settings.SettingsScreen
import app.kweather.ui.settings.LocalFmt
import app.kweather.ui.settings.Fmt
import app.kweather.ui.demo.DemoPager
import app.kweather.ui.demo.DemoGalleryScreen
import app.kweather.ui.demo.DemoData
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kweather.KWeatherApp
import app.kweather.ui.alerts.AlertScreen
import app.kweather.ui.home.HomeUiState
import app.kweather.ui.home.HomeViewModel
import app.kweather.ui.home.PageActions
import app.kweather.ui.home.StatusBarIcons
import app.kweather.ui.home.WeatherPage
import app.kweather.ui.places.PlacesActions
import app.kweather.ui.places.PlacesScreen
import app.kweather.ui.places.PlacesViewModel
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.theme.KWeatherTheme

@Composable
fun AppRoot(
    home: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    places: PlacesViewModel = viewModel(factory = PlacesViewModel.Factory),
    settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by home.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as KWeatherApp
    // Simple back stack of screen names, saved as one string so it survives rotation.
    var stackText by rememberSaveable { mutableStateOf(HOME) }
    val stack = stackText.split('/')
    val screen = stack.last()
    fun push(name: String) { stackText = "$stackText/$name" }
    fun pop() { stackText = stack.dropLast(1).joinToString("/").ifEmpty { HOME } }
    var pendingPage by rememberSaveable { mutableStateOf<String?>(null) }
    var demoStart by rememberSaveable { mutableStateOf(0) }
    // Open alert as "pageKey|alertId".
    var openAlert by rememberSaveable { mutableStateOf<String?>(null) }
    val pager = rememberPagerState { state.pages.size }
    val demoCities = remember { DemoData.cities() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        home.onLocationPermissionResult(granted)
    }
    val requestLocation = {
        if (app.locationProvider.hasPermission()) home.setUseCurrentLocation(true)
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val pageActions = PageActions(
        onRefresh = home::refresh,
        onOpenPlaces = { push(PLACES) },
        onRequestLocation = requestLocation,
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
        onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onOpenAlert = { pageKey, alertId -> openAlert = "$pageKey|$alertId" },
        onOpenSettings = { push(SETTINGS) },
    )
    val demoActions = PageActions(
        onRefresh = {},
        onOpenPlaces = { pop() },
        onRequestLocation = {},
        onOpenAppSettings = {},
        onOpenLocationSettings = {},
        onOpenAlert = { pageKey, alertId -> openAlert = "$pageKey|$alertId" },
        onOpenSettings = { push(SETTINGS) },
    )
    val allPages = state.pages + demoCities.map { it.page }
    val alertPage = openAlert?.substringBefore('|')?.let { key -> allPages.firstOrNull { it.key == key } }
    val alert = alertPage?.alerts?.firstOrNull { it.id == openAlert?.substringAfter('|') }

    // Jump to a page chosen in the Places screen once it exists in the pager.
    LaunchedEffect(pendingPage, state.pages, screen) {
        val key = pendingPage ?: return@LaunchedEffect
        if (screen != HOME) return@LaunchedEffect
        val index = state.pages.indexOfFirst { it.key == key }
        if (index >= 0) {
            pager.scrollToPage(index)
            pendingPage = null
        }
    }

    val current = settings
    if (!state.ready || current == null) {
        Box(Modifier.fillMaxSize().background(ScenePalette.default.skyTop))
        return
    }

    CompositionLocalProvider(LocalFmt provides Fmt(current)) {
        when {
            alert != null -> {
                BackHandler { openAlert = null }
                AlertScreen(alert, alertPage.place?.name.orEmpty()) { openAlert = null }
            }
            screen == PLACES -> {
                BackHandler { pop() }
                KWeatherTheme(placesPalette(state, pager.currentPage)) {
                    PlacesScreen(
                        home = state,
                        vm = places,
                        actions = PlacesActions(
                            onBack = { pop() },
                            onOpenPage = { key ->
                                pendingPage = key
                                places.clear()
                                pop()
                            },
                            onAdd = { place ->
                                home.addPlace(place)
                                pendingPage = place.id
                                places.clear()
                                pop()
                            },
                            onRemove = home::removePlace,
                            onMove = home::movePlace,
                            onToggleCurrent = { enabled -> if (enabled) requestLocation() else home.setUseCurrentLocation(false) },
                        ),
                    )
                }
            }
            screen == SETTINGS -> {
                BackHandler { pop() }
                KWeatherTheme(placesPalette(state, pager.currentPage)) {
                    SettingsScreen(
                        settings = current,
                        onChange = settingsVm::update,
                        onOpenDemo = { push(DEMO_LIST) },
                        onBack = { pop() },
                    )
                }
            }
            screen == DEMO_LIST -> {
                BackHandler { pop() }
                KWeatherTheme(placesPalette(state, pager.currentPage)) {
                    DemoGalleryScreen(demoCities, onOpen = { demoStart = it; push(DEMO) }, onBack = { pop() })
                }
            }
            screen == DEMO -> {
                BackHandler { pop() }
                DemoPager(demoCities, demoStart, demoActions)
            }
            state.pages.isEmpty() -> Welcome(
                state = state,
                onUseLocation = requestLocation,
                onSearch = { push(PLACES) },
            )
            else -> HorizontalPager(
                state = pager,
                key = { state.pages[it].key },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                WeatherPage(state.pages[index], index, state.pages.size, pageActions, active = index == pager.currentPage)
            }
        }
    }
}

private const val HOME = "home"
private const val PLACES = "places"
private const val SETTINGS = "settings"
private const val DEMO_LIST = "demoList"
private const val DEMO = "demo"

/** The Places screen borrows the colors of the page you came from. */
private fun placesPalette(state: HomeUiState, page: Int): ScenePalette =
    state.pages.getOrNull(page)?.weather?.let { ScenePalette.forSpec(SceneSpec.from(it)) } ?: ScenePalette.default

@Composable
private fun Welcome(state: HomeUiState, onUseLocation: () -> Unit, onSearch: () -> Unit) {
    val p = ScenePalette.default
    StatusBarIcons(dark = false)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom))),
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(32.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("KWeather", style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium, color = Color.White))
            Text(
                "Detailed forecasts, lots of wind data, no ads. Where should it start?",
                style = TextStyle(fontSize = 17.sp, color = Color.White),
            )
            state.locationIssue?.let {
                Text(it.message, style = TextStyle(fontSize = 15.sp, color = Color(0xFFFFE3A8)))
            }
            Button(
                onClick = onUseLocation,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = p.skyTop),
            ) { Text("Use my location") }
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text("Search for a city", color = Color.White)
            }
        }
    }
}
