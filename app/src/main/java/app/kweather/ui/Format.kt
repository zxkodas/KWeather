package app.kweather.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

fun Double.deg(): String = "${roundToInt()}°"

fun Double.whole(): String = roundToInt().toString()

fun LocalDateTime.hhmm(): String = format(hourMinute)

fun LocalDateTime.hourLabel(): String = "%02d".format(hour)

fun java.time.LocalDate.shortDay(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

fun Double.durationHm(): String {
    val totalMinutes = (this / 60).roundToInt()
    return "${totalMinutes / 60} h ${totalMinutes % 60} min"
}
