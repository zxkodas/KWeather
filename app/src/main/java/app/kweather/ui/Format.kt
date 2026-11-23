package app.kweather.ui

import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// Units and clock formats live in ui.settings.Fmt; these helpers are unit-free.

fun Double.whole(): String = roundToInt().toString()

fun java.time.LocalDate.shortDay(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

fun Double.durationHm(): String {
    val totalMinutes = (this / 60).roundToInt()
    return "${totalMinutes / 60} h ${totalMinutes % 60} min"
}
