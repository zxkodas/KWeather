package app.kweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.kweather.ui.scene.LocalPalette
import app.kweather.ui.scene.ScenePalette

/** The whole screen's colors come from the current weather scene. */
@Composable
fun KWeatherTheme(palette: ScenePalette, content: @Composable () -> Unit) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.card,
            surfaceVariant = palette.cardHighlight,
            onBackground = palette.onCard,
            onSurface = palette.onCard,
            onSurfaceVariant = palette.onCardVariant,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.card,
            surfaceVariant = palette.cardHighlight,
            onBackground = palette.onCard,
            onSurface = palette.onCard,
            onSurfaceVariant = palette.onCardVariant,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
