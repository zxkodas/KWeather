package app.kweather.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.kweather.R

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/**
 * Archivo (variable, SIL OFL) is used for figures only: temperatures, wind, tile values, chart labels.
 * Its sturdy grotesque digits read like an instrument panel; words stay in the system font.
 */
val NumberFamily = FontFamily(
    archivo(400, 100f),
    archivo(500, 100f),
    archivo(600, 100f),
    archivo(700, 100f),
)

/** The hero temperature: semi-expanded and heavy, the one loud thing on the screen. */
val HeroNumberFamily = FontFamily(archivo(700, 112f))

/** Tabular digits so columns of numbers line up and don't jitter when values change. */
const val TABULAR = "tnum"

/** Base style for figures; copy it with a size and color. */
val Figures = TextStyle(fontFamily = NumberFamily, fontFeatureSettings = TABULAR)
