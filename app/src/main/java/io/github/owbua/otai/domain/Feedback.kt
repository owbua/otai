package io.github.owbua.otai.domain

import io.github.owbua.otai.data.model.Note
import io.github.owbua.otai.data.model.Recipe
import kotlin.math.abs

/**
 * Wendet die Rückmeldung nach dem Kochen auf ein Rezept an.
 *
 * @param cookedServings Portionen, mit denen gekocht wurde
 * @param cookedAmounts  Zutaten-Key → neue Menge *für die gekochten Portionen* (null = n. B.).
 *                       Nicht enthaltene Keys bleiben unverändert.
 * @return das angepasste Rezept: Mengen auf die Basisportionen umgerechnet, neue datierte Notiz,
 *         Kochzähler und Bewertung aktualisiert.
 */
fun Recipe.applyFeedback(
    cookedServings: Int,
    cookedAmounts: Map<String, Double?>,
    text: String,
    rating: Int?,
    today: String,
): Recipe {
    val factor = cookedServings.toDouble() / servings
    val changes = mutableListOf<String>()
    val newIngredients = ingredients.map { ing ->
        val key = ing.key ?: return@map ing
        if (!cookedAmounts.containsKey(key)) return@map ing
        val cooked = cookedAmounts[key]
        val oldCooked = ing.amount?.times(factor)
        val same = when {
            cooked == null && oldCooked == null -> true
            cooked == null || oldCooked == null -> false
            else -> abs(cooked - oldCooked) <= maxOf(0.01, oldCooked * 0.01)
        }
        if (same) return@map ing
        val unit = ing.unit?.let { " $it" } ?: ""
        val before = oldCooked?.let { formatNumber(it, ing.unit) + unit } ?: "n. B."
        val after = cooked?.let { formatNumber(it, ing.unit) + unit } ?: "n. B."
        changes += "${ing.name}: $before → $after"
        ing.copy(amount = cooked?.div(factor))
    }
    val trimmed = text.trim()
    val withPortions = if (changes.isNotEmpty() && cookedServings != servings) {
        changes.map { "$it (für ${servingsLabel(cookedServings)})" }
    } else changes
    val newNotes = if (trimmed.isEmpty() && changes.isEmpty() && rating == null) notes
    else notes + Note(date = today, text = trimmed, changes = withPortions, rating = rating)
    return copy(
        ingredients = newIngredients,
        notes = newNotes,
        rating = rating ?: this.rating,
        cookedCount = cookedCount + 1,
        lastCooked = today,
    )
}
