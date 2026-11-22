package app.kweather.ui.scene

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colors for both the animated scene and the cards below it. */
@Immutable
data class ScenePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val cloud: Color,
    val cloudShade: Color,
    val precipitation: Color,
    val hillFar: Color,
    val hillMid: Color,
    val background: Color,
    val card: Color,
    val cardHighlight: Color,
    val onCard: Color,
    val onCardVariant: Color,
    val accent: Color,
    val gust: Color,
    val rain: Color,
    val isDark: Boolean,
) {
    companion object {
        fun forSpec(spec: SceneSpec): ScenePalette {
            val clear = clearSky(spec.time)
            val (top, bottom) = when (spec.sky) {
                SkyType.Clear -> clear
                SkyType.PartlyCloudy -> mix(clear, greySky(spec.time), 0.25f)
                SkyType.Overcast -> mix(clear, greySky(spec.time), 0.85f)
                SkyType.Fog -> mix(clear, fogSky(spec.time), 0.9f)
                SkyType.Drizzle -> mix(clear, greySky(spec.time), 0.75f)
                SkyType.Rain -> mix(clear, greySky(spec.time), 0.9f)
                SkyType.Storm -> stormSky(spec.time)
                SkyType.Snow -> snowSky(spec.time)
            }

            val isDark = spec.time == TimeOfDay.Night ||
                spec.sky == SkyType.Storm || spec.sky == SkyType.Rain ||
                (spec.sky == SkyType.Overcast && spec.time != TimeOfDay.Day)

            val background: Color
            val card: Color
            val highlight: Color
            val onCard: Color
            val onVariant: Color
            if (isDark) {
                background = lerp(top, Color.Black, 0.35f)
                card = lerp(background, Color.White, 0.07f)
                highlight = lerp(background, Color.White, 0.14f)
                onCard = Color(0xFFE8ECF2)
                onVariant = Color(0xFFAEB7C4)
            } else {
                background = lerp(bottom, Color.White, 0.86f)
                card = lerp(bottom, Color.White, 0.66f)
                highlight = Color.White
                onCard = lerp(top, Color.Black, 0.75f)
                onVariant = lerp(top, Color.Black, 0.5f)
            }

            val greenery = spec.sky != SkyType.Snow && spec.time != TimeOfDay.Night && !isDark
            val cloudiness = when (spec.sky) {
                SkyType.Clear -> 0f
                SkyType.PartlyCloudy -> 0.2f
                else -> 0.6f
            }
            val hillFar = when {
                spec.sky == SkyType.Snow -> lerp(bottom, Color.White, 0.55f)
                greenery -> lerp(Color(0xFF7DB894), bottom, cloudiness * 0.6f + if (spec.time == TimeOfDay.Day) 0f else 0.35f)
                else -> lerp(bottom, background, 0.45f)
            }
            val hillMid = when {
                spec.sky == SkyType.Snow -> lerp(hillFar, background, 0.5f)
                greenery -> lerp(Color(0xFF5EA277), bottom, cloudiness * 0.6f + if (spec.time == TimeOfDay.Day) 0f else 0.35f)
                else -> lerp(background, bottom, 0.18f)
            }

            val cloud = when {
                spec.sky == SkyType.Storm -> lerp(top, bottom, 0.55f)
                isDark -> lerp(bottom, Color.White, 0.1f)
                spec.sky == SkyType.Clear || spec.sky == SkyType.PartlyCloudy -> Color.White
                else -> lerp(bottom, Color.White, 0.35f)
            }

            val accent = when {
                spec.sky == SkyType.Storm || spec.sky == SkyType.Rain -> Color(0xFFF3BD4E)
                spec.time == TimeOfDay.Night -> Color(0xFFC9D3FF)
                isDark -> Color(0xFFF3BD4E)
                else -> lerp(top, Color.Black, 0.3f)
            }

            return ScenePalette(
                skyTop = top,
                skyBottom = bottom,
                cloud = cloud,
                cloudShade = lerp(cloud, top, 0.25f),
                precipitation = if (spec.sky == SkyType.Snow) Color.White else lerp(bottom, Color.White, 0.55f),
                hillFar = hillFar,
                hillMid = hillMid,
                background = background,
                card = card,
                cardHighlight = highlight,
                onCard = onCard,
                onCardVariant = onVariant,
                accent = accent,
                gust = if (isDark) Color(0xFFF3BD4E) else Color(0xFFB0640A),
                rain = if (isDark) Color(0xFF8EC1FF) else Color(0xFF2F64A8),
                isDark = isDark,
            )
        }

        private fun mix(a: Pair<Color, Color>, b: Pair<Color, Color>, t: Float) =
            lerp(a.first, b.first, t) to lerp(a.second, b.second, t)

        private fun clearSky(t: TimeOfDay) = when (t) {
            TimeOfDay.Day -> Color(0xFF1A63AE) to Color(0xFF8CC4EC)
            TimeOfDay.Dawn -> Color(0xFF34437E) to Color(0xFFF0A981)
            TimeOfDay.Dusk -> Color(0xFF2B2D68) to Color(0xFFE58767)
            TimeOfDay.Night -> Color(0xFF070D24) to Color(0xFF2A3770)
        }

        private fun greySky(t: TimeOfDay) = when (t) {
            TimeOfDay.Day -> Color(0xFF55636F) to Color(0xFF9FAAB5)
            TimeOfDay.Dawn -> Color(0xFF4D5266) to Color(0xFFA8918B)
            TimeOfDay.Dusk -> Color(0xFF3E4258) to Color(0xFF8E7E86)
            TimeOfDay.Night -> Color(0xFF12161E) to Color(0xFF323A48)
        }

        private fun fogSky(t: TimeOfDay) = when (t) {
            TimeOfDay.Night -> Color(0xFF1C2129) to Color(0xFF4A515C)
            else -> Color(0xFF6E7A85) to Color(0xFFC2C9CF)
        }

        private fun stormSky(t: TimeOfDay) = when (t) {
            TimeOfDay.Night -> Color(0xFF0E1218) to Color(0xFF2A303A)
            else -> Color(0xFF232B38) to Color(0xFF4B5566)
        }

        private fun snowSky(t: TimeOfDay) = when (t) {
            TimeOfDay.Night -> Color(0xFF0E1826) to Color(0xFF5A6F85)
            else -> Color(0xFF6F8296) to Color(0xFFC9D4DE)
        }
    }
}

val LocalPalette = staticCompositionLocalOf<ScenePalette> {
    error("ScenePalette not provided")
}
