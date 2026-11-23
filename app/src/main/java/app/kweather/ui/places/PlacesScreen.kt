package app.kweather.ui.places

import app.kweather.ui.settings.LocalFmt
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kweather.R
import app.kweather.domain.Place
import app.kweather.ui.home.HomeUiState
import app.kweather.ui.home.StatusBarIcons
import app.kweather.ui.scene.LocalPalette

class PlacesActions(
    val onBack: () -> Unit,
    val onOpenPage: (key: String) -> Unit,
    val onAdd: (Place) -> Unit,
    val onRemove: (id: String) -> Unit,
    val onMove: (id: String, delta: Int) -> Unit,
    val onToggleCurrent: (Boolean) -> Unit,
)

@Composable
fun PlacesScreen(home: HomeUiState, vm: PlacesViewModel, actions: PlacesActions) {
    val p = LocalPalette.current
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    StatusBarIcons(dark = !p.isDark)

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = p.onCard)
            }
            Text("Places", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = p.onCard))
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search for a city") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = p.onCardVariant) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = vm::clear) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Clear search",
                            tint = p.onCardVariant, modifier = Modifier.rotate(45f))
                    }
                }
            } else {
                null
            },
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = p.card,
                unfocusedContainerColor = p.card,
                focusedBorderColor = p.accent,
                unfocusedBorderColor = p.card,
                focusedTextColor = p.onCard,
                unfocusedTextColor = p.onCard,
                cursorColor = p.accent,
                focusedPlaceholderColor = p.onCardVariant,
                unfocusedPlaceholderColor = p.onCardVariant,
            ),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isNotBlank()) {
                when (val s = search) {
                    SearchState.Idle -> item { Hint("Type at least two letters.") }
                    SearchState.Searching -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = p.accent)
                        }
                    }
                    is SearchState.Failed -> item { Hint(s.message) }
                    is SearchState.Results -> {
                        if (s.places.isEmpty()) item { Hint("No places found for \"$query\".") }
                        items(s.places, key = { it.id }) { place ->
                            val saved = home.saved.any { it.id == place.id }
                            PlaceRow(
                                title = place.name,
                                subtitle = place.region,
                                trailing = if (saved) "Saved" else null,
                                onClick = { if (saved) actions.onOpenPage(place.id) else actions.onAdd(place) },
                            ) {
                                if (!saved) Icon(painterResource(R.drawable.ic_add), contentDescription = "Add ${place.name}", tint = p.accent)
                            }
                        }
                    }
                }
            } else {
                item { CurrentLocationRow(home, actions) }
                item {
                    Text(
                        "Saved places",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.onCardVariant),
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    )
                }
                if (home.saved.isEmpty()) {
                    item { Hint("Search above to add a city.") }
                }
                items(home.saved, key = { it.id }) { place ->
                    val index = home.saved.indexOf(place)
                    val temp = home.pages.firstOrNull { it.key == place.id }?.weather?.current?.temperature?.let(LocalFmt.current::temp)
                    PlaceRow(
                        title = place.name,
                        subtitle = place.region,
                        trailing = temp,
                        onClick = { actions.onOpenPage(place.id) },
                    ) {
                        IconButton(onClick = { actions.onMove(place.id, -1) }, enabled = index > 0) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Move ${place.name} up",
                                tint = if (index > 0) p.onCardVariant else p.onCardVariant.copy(alpha = 0.3f),
                                modifier = Modifier.rotate(90f))
                        }
                        IconButton(onClick = { actions.onMove(place.id, 1) }, enabled = index < home.saved.lastIndex) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Move ${place.name} down",
                                tint = if (index < home.saved.lastIndex) p.onCardVariant else p.onCardVariant.copy(alpha = 0.3f),
                                modifier = Modifier.rotate(-90f))
                        }
                        IconButton(onClick = { actions.onRemove(place.id) }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove ${place.name}", tint = p.onCardVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun CurrentLocationRow(home: HomeUiState, actions: PlacesActions) {
    val p = LocalPalette.current
    val current = home.pages.firstOrNull { it.isCurrent }
    val subtitle = when {
        home.locationIssue != null -> home.locationIssue.message
        !home.useCurrentLocation -> "Show the forecast where you are"
        current?.place != null -> current.place.name
        else -> "Finding your location…"
    }
    PlaceRow(
        title = "Use my location",
        subtitle = subtitle,
        trailing = current?.weather?.current?.temperature?.let(LocalFmt.current::temp)?.takeIf { home.useCurrentLocation },
        onClick = if (home.useCurrentLocation && current != null) {
            { actions.onOpenPage(current.key) }
        } else {
            { actions.onToggleCurrent(true) }
        },
        leading = {
            Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = p.accent, modifier = Modifier.size(20.dp))
        },
    ) {
        Switch(
            checked = home.useCurrentLocation,
            onCheckedChange = actions.onToggleCurrent,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent),
        )
    }
}

@Composable
private fun PlaceRow(
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(p.card, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.onCard),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = TextStyle(fontSize = 13.sp, color = p.onCardVariant), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) Text(trailing, style = TextStyle(fontSize = 18.sp, color = p.onCard))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun Hint(text: String) {
    val p = LocalPalette.current
    Text(text, style = TextStyle(fontSize = 14.sp, color = p.onCardVariant), modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}
