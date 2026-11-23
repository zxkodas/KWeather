package app.kweather.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kweather.R
import app.kweather.ui.home.PageActions
import app.kweather.ui.home.StatusBarIcons
import app.kweather.ui.home.WeatherPage
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.scene.SceneSpec
import app.kweather.ui.scene.ScenePalette
import app.kweather.ui.settings.LocalFmt

@Composable
fun DemoGalleryScreen(cities: List<DemoCity>, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val f = LocalFmt.current
    StatusBarIcons(dark = !p.isDark)
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = p.onCard)
            }
            Text("Demo gallery", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = p.onCard))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Example data, not real forecasts. Open one and swipe through them all.",
                    style = TextStyle(fontSize = 14.sp, color = p.onCardVariant),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(cities, key = { _, c -> c.page.key }) { index, city ->
                val weather = city.page.weather!!
                val palette = remember(weather) { ScenePalette.forSpec(SceneSpec.from(weather)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .background(p.card, RoundedCornerShape(20.dp))
                        .clickable { onOpen(index) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom)), CircleShape),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(city.title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.onCard))
                        Text(city.description, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant))
                    }
                    Text(f.temp(weather.current.temperature), style = TextStyle(fontSize = 18.sp, color = p.onCard))
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
fun DemoPager(cities: List<DemoCity>, start: Int, actions: PageActions) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (cities.size - 1).coerceAtLeast(0))) { cities.size }
    HorizontalPager(state = pager, key = { cities[it].page.key }, modifier = Modifier.fillMaxSize()) { index ->
        WeatherPage(cities[index].page, index, cities.size, actions, active = index == pager.currentPage)
    }
}
