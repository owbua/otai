package io.github.owbua.otai.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val WEEKDAY_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val WEEKDAY_LONG = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M.")

fun weekdayShort(d: LocalDate) = WEEKDAY_SHORT[d.dayOfWeek.value - 1]
fun weekdayLong(d: LocalDate) = WEEKDAY_LONG[d.dayOfWeek.value - 1]

/** „Heute“, „Morgen“, „Gestern“ oder „Montag, 16.11.“ */
fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
    0L -> "Heute"
    1L -> "Morgen"
    -1L -> "Gestern"
    else -> "${weekdayLong(d)}, ${d.format(DAY_MONTH)}"
}

fun shortDate(d: LocalDate): String = "${weekdayShort(d)} ${d.format(DAY_MONTH)}"

/** „Diese Woche“, „Nächste Woche“, „Letzte Woche“, sonst „KW 48“. */
fun weekLabel(monday: LocalDate, today: LocalDate): String {
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return when (ChronoUnit.WEEKS.between(thisMonday, monday)) {
        0L -> "Diese Woche"
        1L -> "Nächste Woche"
        -1L -> "Letzte Woche"
        else -> "KW ${monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }
}
