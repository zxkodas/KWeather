package app.kweather.ui.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/** WCAG 2.x relative luminance of an opaque sRGB color. */
fun relativeLuminance(c: Color): Double {
    fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** WCAG contrast ratio, 1 to 21. Text needs 4.5 (3 when large), UI shapes need 3. */
fun contrast(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** [over] painted on top of opaque [base], using [over]'s alpha. */
fun composite(base: Color, over: Color): Color = lerp(base, over.copy(alpha = 1f), over.alpha)
