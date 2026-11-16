package io.github.owbua.otai.domain

import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.Recipe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class Suggestion(val recipe: Recipe, val reason: String)

/**
 * Schlägt ein Rezept für einen Tag vor – nach ähnlichen Regeln wie openCook (eigene Umsetzung):
 * Abwechslung, gemeinsame Zutaten in derselben Woche, Favoriten/Bewertung, lange nicht gekocht,
 * an Wochentagen eher schnelle Gerichte. Liefert immer eine Begründung.
 */
object MealSuggester {
    private const val RECENT_DAYS = 10L
    private val SHORT_DAY = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun suggest(
        date: LocalDate,
        recipes: List<Recipe>,
        state: KitchenState,
        today: LocalDate,
        exclude: Set<String> = emptySet(),
    ): Suggestion? {
        val candidates = recipes.filter { it.id !in exclude && (it.steps.isNotEmpty() || it.ingredients.isNotEmpty()) }
        if (candidates.isEmpty()) return null
        val byId = recipes.associateBy { it.id }
        val weekStart = date.with(DayOfWeek.MONDAY)
        val weekEntries = state.plan.filter { e ->
            val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@filter false
            d != date && !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7))
        }

        data class Scored(val recipe: Recipe, val score: Double, val reason: String, val reasonWeight: Double)

        val scored = candidates.map { r ->
            var score = 0.0
            val reasons = mutableListOf<Pair<Double, String>>()

            // Abwechslung: kürzlich geplant oder gekocht → stark abwerten
            val plannedNear = state.plan.any { e ->
                e.recipeId == r.id && runCatching { abs(ChronoUnit.DAYS.between(LocalDate.parse(e.date), date)) <= RECENT_DAYS }.getOrDefault(false)
            }
            val cookedRecently = r.lastCooked?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today) <= RECENT_DAYS }.getOrNull() } == true
            if (plannedNear || cookedRecently) score -= 10

            if (r.favorite) { score += 3; reasons += 3.0 to "Einer deiner Favoriten" }
            r.rating?.let { rating ->
                score += (rating - 3) * 0.7
                if (rating >= 4) reasons += (rating - 3) * 0.7 to "Zuletzt mit $rating von 5 Sternen bewertet"
            }

            // Gemeinsame Zutaten mit dem Rest der Woche
            val myStems = r.ingredients.filterNot { isStaple(it.name, state.staples) }.associateBy { ingredientStem(it.name) }
            var bestShare: Pair<List<String>, String>? = null
            var shareTotal = 0
            for (e in weekEntries) {
                val other = byId[e.recipeId] ?: continue
                val shared = other.ingredients.filter { ingredientStem(it.name) in myStems }.map { myStems.getValue(ingredientStem(it.name)).name.substringBefore(',').substringBefore('(').trim() }.distinct()
                shareTotal += shared.size
                if (shared.isNotEmpty() && (bestShare == null || shared.size > bestShare.first.size)) {
                    val day = runCatching { SHORT_DAY[LocalDate.parse(e.date).dayOfWeek.value - 1] }.getOrDefault("")
                    bestShare = shared to "${other.title} am $day"
                }
            }
            if (shareTotal > 0) {
                val w = minOf(shareTotal, 3).toDouble()
                score += w
                bestShare?.let { (items, where) ->
                    reasons += w to "Nutzt ebenfalls ${items.take(2).joinToString(" und ")} (wie $where)"
                }
            }

            // Lange nicht oder noch nie gekocht
            val last = r.lastCooked?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (last == null && r.cookedCount == 0) {
                score += 0.8; reasons += 0.8 to "Noch nie gekocht – Zeit, es auszuprobieren"
            } else if (last != null) {
                val weeks = ChronoUnit.WEEKS.between(last, today)
                if (weeks >= 4) { score += 1.0; reasons += 1.0 to "Zuletzt vor $weeks Wochen gekocht" }
            }

            // Wochentags eher schnell
            val minutes = r.times.totalMin ?: r.times.activeMin
            if (date.dayOfWeek.value <= 5 && minutes != null && minutes <= 30) {
                score += 0.5; reasons += 0.5 to "Schnell gemacht für einen Wochentag"
            } else if (date.dayOfWeek.value >= 6 && minutes != null && minutes >= 60) {
                score += 0.4; reasons += 0.4 to "Am Wochenende ist Zeit für Aufwendigeres"
            }

            // Kleine, aber stabile Streuung je Tag, damit nicht immer dasselbe vorne liegt
            score += ((r.id + date).hashCode().toLong().let { abs(it) % 1000 } / 1000.0) * 0.3

            val top = reasons.maxByOrNull { it.first }
            Scored(r, score, top?.second ?: "Passt gut in deinen Plan", top?.first ?: 0.0)
        }
        val best = scored.maxByOrNull { it.score } ?: return null
        return Suggestion(best.recipe, best.reason)
    }
}
