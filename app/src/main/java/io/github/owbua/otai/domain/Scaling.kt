package io.github.owbua.otai.domain

import io.github.owbua.otai.data.model.Ingredient
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Einheiten, bei denen Brüche („½ TL“) natürlicher sind als Dezimalzahlen. */
private val METRIC_UNITS = setOf("g", "kg", "ml", "l", "cl", "dl", "mg")

private val NICE_FRACTIONS = listOf(
    0.0 to "", 0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾", 1.0 to "",
)

/** Formatiert eine Zahl ohne Einheit (deutsch, Komma). */
fun formatNumber(value: Double, unit: String? = null): String {
    val metric = unit?.lowercase(Locale.GERMAN) in METRIC_UNITS
    if (metric) {
        return when {
            value >= 100 -> ((value / 5).roundToInt() * 5).toString()
            value >= 10 -> value.roundToInt().toString()
            else -> decimal(value)
        }
    }
    if (value >= 10) return value.roundToInt().toString()
    val whole = floor(value)
    val frac = value - whole
    val (fv, fs) = NICE_FRACTIONS.minBy { abs(it.first - frac) }
    // Nur Brüche verwenden, wenn sie nahe genug liegen; sonst Dezimalzahl
    if (abs(fv - frac) > 0.06) return decimal(value)
    val w = (whole + if (fv == 1.0) 1 else 0).toInt()
    return when {
        fs.isEmpty() -> w.toString()
        w == 0 -> fs
        else -> "$w $fs"
    }.ifEmpty { "0" }
}

private fun decimal(value: Double): String {
    val r = (value * 10).roundToInt() / 10.0
    return if (r == floor(r)) r.toInt().toString() else r.toString().replace('.', ',')
}

/** „500 g“, „2“, „½ Bund“, „n. B.“ – Menge einer Zutat für den gegebenen Faktor. */
fun Ingredient.amountText(factor: Double = 1.0): String {
    val a = amount ?: return unit?.let { it } ?: "n. B."
    val n = formatNumber(a * factor, unit)
    return if (unit.isNullOrBlank()) n else "$n $unit"
}

fun formatMinutes(min: Int?): String? {
    if (min == null || min <= 0) return null
    if (min < 60) return "$min Min"
    val h = min / 60
    val m = min % 60
    return if (m == 0) "$h Std" else "$h Std $m"
}

fun formatTimer(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%02d:%02d".format(m, r)
}

fun formatTimerShort(seconds: Int): String {
    val min = (seconds + 59) / 60
    return formatMinutes(min) ?: "$seconds s"
}

fun servingsLabel(n: Int) = if (n == 1) "1 Portion" else "$n Portionen"
