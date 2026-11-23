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
    /** Distant ground at the horizon. */
    val groundFar: Color,
    /** Near ground and the grass blades on it. */
    val grass: Color,
    val background: Color,
    val card: Color,
    val cardHighlight: Color,
    val onCard: Color,
    val onCardVariant: Color,
    val accent: Color,
    val gust: Color,
    val rain: Color,
    /** Sun glyphs and the sun arc on cards; deeper amber on light cards so it stays visible. */
    val sunInk: Color,
    /** Text and icons over the sky in the hero. */
    val heroInk: Color,
    /** Veil painted behind the hero text (alpha included) so [heroInk] reads over any part of the sky. */
    val heroScrim: Color,
    /** The wind panel's own background, a darker slice of the sky, top and bottom. */
    val windCard: Color,
    val windCardEnd: Color,
    val onWind: Color,
    val onWindVariant: Color,
    val isDark: Boolean,
) {
    val heroInkIsDark: Boolean get() = relativeLuminance(heroInk) < 0.2

    companion object {
        /** Clear-day palette for screens that have no forecast to take colors from. */
        val default: ScenePalette by lazy {
            forSpec(SceneSpec(SkyType.Clear, TimeOfDay.Day, 0.0, 0.0, 0.0, 0.0, heavy = false))
        }

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
                onVariant = Color(0xFFBFC7D2)
            } else {
                // Keep the scene's hue in the content instead of washing it out to near-white.
                background = lerp(bottom, Color.White, 0.72f)
                card = lerp(bottom, Color.White, 0.52f)
                highlight = lerp(bottom, Color.White, 0.85f)
                onCard = lerp(top, Color.Black, 0.75f)
                onVariant = lerp(top, Color.Black, 0.5f)
            }

            val greenery = spec.sky != SkyType.Snow && spec.time != TimeOfDay.Night && !isDark
            val cloudiness = when (spec.sky) {
                SkyType.Clear -> 0f
                SkyType.PartlyCloudy -> 0.2f
                else -> 0.6f
            }
            val dim = cloudiness * 0.6f + if (spec.time == TimeOfDay.Day) 0f else 0.35f
            val groundFar = when {
                spec.sky == SkyType.Snow -> lerp(bottom, Color.White, 0.55f)
                greenery -> lerp(Color(0xFF8FB58A), bottom, dim)
                else -> lerp(bottom, background, 0.45f)
            }
            val grass = when {
                spec.sky == SkyType.Snow -> lerp(top, Color.Black, 0.35f)
                greenery -> lerp(Color(0xFF557F3E), bottom, dim)
                isDark -> lerp(top, Color.Black, 0.55f)
                else -> lerp(bottom, top, 0.7f)
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

            val cloudShade = lerp(cloud, top, 0.25f)
            val (heroInk, heroScrim) = heroInk(spec, top, bottom, cloud, cloudShade)
            val (windCard, windCardEnd) = windCard(top, bottom)

            return ScenePalette(
                skyTop = top,
                skyBottom = bottom,
                cloud = cloud,
                cloudShade = cloudShade,
                precipitation = if (spec.sky == SkyType.Snow) Color.White else lerp(bottom, Color.White, 0.55f),
                groundFar = groundFar,
                grass = grass,
                background = background,
                card = card,
                cardHighlight = highlight,
                onCard = onCard,
                onCardVariant = onVariant,
                accent = accent,
                gust = if (isDark) SUN else Color(0xFF7A3F00),
                rain = if (isDark) Color(0xFF8EC1FF) else Color(0xFF234F8C),
                sunInk = if (isDark) SUN else Color(0xFF946000),
                heroInk = heroInk,
                heroScrim = heroScrim,
                windCard = windCard,
                windCardEnd = windCardEnd,
                onWind = Color.White,
                onWindVariant = lerp(windCard, Color.White, 0.8f),
                isDark = isDark,
            )
        }

        /** Where the sky gradient's middle stop sits; above it the sky is a plain top-to-bottom blend. */
        const val SKY_MID = 0.85f

        /** Sky color at a fraction of the hero height: top to bottom, then a lighter band at the horizon. */
        fun skyAt(top: Color, bottom: Color, f: Float): Color =
            if (f <= SKY_MID) lerp(top, bottom, f) else lerp(lerp(top, bottom, SKY_MID), horizon(bottom), (f - SKY_MID) / (1 - SKY_MID))

        fun horizon(bottom: Color): Color = lerp(bottom, Color.White, 0.18f)

        /** The sun yellow used across the scene and on dark cards. */
        val SUN = Color(0xFFF3BD4E)

        /** Hero text can reach this far down the hero at its smallest height. */
        private const val HERO_TEXT_REACH = 0.85f

        /** Everything the hero text can land on: the sky down to the text's reach, plus cloud decks and fog. */
        fun heroBackdrops(spec: SceneSpec, top: Color, bottom: Color, cloud: Color, cloudShade: Color): List<Color> {
            val sky = (0..10).map { skyAt(top, bottom, it / 10f * HERO_TEXT_REACH) }
            return when (spec.sky) {
                SkyType.Overcast, SkyType.Drizzle, SkyType.Rain, SkyType.Storm, SkyType.Snow -> sky + cloud + cloudShade
                SkyType.Fog -> sky + sky.map { lerp(it, lerp(bottom, Color.White, 0.5f), 0.5f) }
                else -> sky
            }
        }

        /**
         * White text over a veil tinted from the sky top, or dark text over a light veil, whichever needs
         * the lighter veil (white wins ties up to a 45% veil). Every backdrop reaches 4.6:1.
         */
        private fun heroInk(spec: SceneSpec, top: Color, bottom: Color, cloud: Color, cloudShade: Color): Pair<Color, Color> {
            val backdrops = heroBackdrops(spec, top, bottom, cloud, cloudShade)
            fun veil(ink: Color, tint: Color): Float? = (0..40).map { it * 0.02f }.firstOrNull { a ->
                backdrops.all { contrast(ink, composite(it, tint.copy(alpha = a))) >= 4.6 }
            }
            val darkTint = lerp(top, Color.Black, 0.6f)
            val lightTint = lerp(bottom, Color.White, 0.6f)
            val darkInk = lerp(top, Color.Black, 0.82f)
            val white = veil(Color.White, darkTint)
            val dark = veil(darkInk, lightTint)
            return if (white != null && (white <= 0.45f || dark == null || white <= dark)) {
                Color.White to darkTint.copy(alpha = white)
            } else {
                darkInk to lightTint.copy(alpha = dark ?: 0.8f)
            }
        }

        /** A mid slice of the sky, darkened until white text and the gust yellow read on both ends. */
        private fun windCard(top: Color, bottom: Color): Pair<Color, Color> {
            var start = lerp(top, bottom, 0.45f)
            var end = lerp(start, bottom, 0.2f)
            repeat(20) {
                val ok = listOf(start, end).all { contrast(Color.White, it) >= 6.0 && contrast(SUN, it) >= 4.5 }
                if (ok) return start to end
                start = lerp(start, Color.Black, 0.08f)
                end = lerp(end, Color.Black, 0.08f)
            }
            return start to end
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
