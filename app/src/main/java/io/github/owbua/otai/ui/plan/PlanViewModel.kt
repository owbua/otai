package io.github.owbua.otai.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.PlanEntry
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.domain.MealSuggester
import io.github.owbua.otai.domain.ShoppingPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanItemUi(val entry: PlanEntry, val recipe: Recipe)
data class PlanDayUi(val date: LocalDate, val items: List<PlanItemUi>)
data class PlanWeekUi(val monday: LocalDate, val days: List<PlanDayUi>)

data class PlanUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val weeks: List<PlanWeekUi> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val plannedCount: Int = 0,
    val openShopping: Int = 0,
)

/** Aktuelle Woche plus zwei weitere; vergangene leere Tage werden ausgeblendet. */
fun buildPlan(recipes: List<Recipe>?, kitchen: KitchenState?, today: LocalDate, weeks: Int = 3): PlanUiState {
    if (recipes == null || kitchen == null) return PlanUiState(loading = true, today = today)
    val byId = recipes.associateBy { it.id }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val entriesByDate = kitchen.plan.groupBy { it.date }
    val weekList = (0 until weeks).map { w ->
        val start = monday.plusWeeks(w.toLong())
        PlanWeekUi(start, (0..6).map { d ->
            val date = start.plusDays(d.toLong())
            PlanDayUi(date, entriesByDate[date.toString()].orEmpty().mapNotNull { e -> byId[e.recipeId]?.let { PlanItemUi(e, it) } })
        }.filter { it.items.isNotEmpty() || !it.date.isBefore(today) })
    }
    val upcoming = ShoppingPlanner.upcoming(kitchen, today).count { byId.containsKey(it.recipeId) }
    val open = ShoppingPlanner.build(kitchen, recipes, today).count { !it.checked }
    return PlanUiState(false, today, weekList, recipes.sortedBy { it.title.lowercase() }, upcoming, open)
}

class PlanViewModel(
    private val repo: RecipeRepository,
    private val kitchen: KitchenRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val today = MutableStateFlow(clock())

    val state: StateFlow<PlanUiState> = combine(repo.recipes, kitchen.state, today) { r, k, t -> buildPlan(r, k, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanUiState())

    /** Beim Zurückkehren auf den Screen „heute“ aktualisieren (z. B. nach Mitternacht). */
    fun refreshToday() { today.value = clock() }

    fun pick(date: LocalDate, recipe: Recipe) = viewModelScope.launch { kitchen.addToPlan(date, recipe.id, recipe.servings) }

    fun swap(entry: PlanEntry, recipe: Recipe) = viewModelScope.launch {
        kitchen.replaceInPlan(entry.id, recipe.id, recipe.servings, reason = null)
    }

    fun suggest(date: LocalDate) = viewModelScope.launch {
        val k = kitchen.current()
        val planned = k.plan.filter { it.date == date.toString() }.map { it.recipeId }.toSet()
        val s = MealSuggester.suggest(date, repo.all(), k, today.value, exclude = planned) ?: return@launch
        kitchen.addToPlan(date, s.recipe.id, s.recipe.servings, s.reason)
    }

    /** Anderen Vorschlag für einen Eintrag – schließt bisher vorgeschlagene aus. */
    private val rejected = mutableMapOf<String, MutableSet<String>>()

    fun suggestAnother(entry: PlanEntry) = viewModelScope.launch {
        val date = LocalDate.parse(entry.date)
        val ex = rejected.getOrPut(entry.id) { mutableSetOf() }.apply { add(entry.recipeId) }
        val all = repo.all()
        val k = kitchen.current().let { s -> s.copy(plan = s.plan.filterNot { it.id == entry.id }) }
        val s = MealSuggester.suggest(date, all, k, today.value, exclude = ex)
            ?: run { ex.clear(); ex.add(entry.recipeId); MealSuggester.suggest(date, all, k, today.value, exclude = ex) }
            ?: return@launch
        kitchen.replaceInPlan(entry.id, s.recipe.id, s.recipe.servings, s.reason)
    }

    fun remove(entry: PlanEntry) = viewModelScope.launch { kitchen.removeFromPlan(entry.id) }
    fun setServings(entry: PlanEntry, servings: Int) = viewModelScope.launch { kitchen.setPlanServings(entry.id, servings) }
    fun setCooked(entry: PlanEntry, cooked: Boolean) = viewModelScope.launch { kitchen.setPlanCooked(entry.id, cooked) }
}
