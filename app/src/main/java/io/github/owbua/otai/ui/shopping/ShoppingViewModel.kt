package io.github.owbua.otai.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.PantryItem
import io.github.owbua.otai.domain.ShoppingLine
import io.github.owbua.otai.domain.ShoppingPlanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ShoppingUiState(
    val loading: Boolean = true,
    val lines: List<ShoppingLine> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
    val staples: List<String> = emptyList(),
    val plannedCount: Int = 0,
) {
    val checkedCount get() = lines.count { it.checked }
    val openCount get() = lines.count { !it.checked }
}

class ShoppingViewModel(
    repo: RecipeRepository,
    private val kitchen: KitchenRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val state: StateFlow<ShoppingUiState> = combine(repo.recipes, kitchen.state) { r, k ->
        if (r == null || k == null) ShoppingUiState()
        else ShoppingUiState(
            loading = false,
            lines = ShoppingPlanner.build(k, r, clock()),
            pantry = k.pantry.sortedBy { it.name.lowercase() },
            staples = k.staples,
            plannedCount = ShoppingPlanner.upcoming(k, clock()).size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingUiState())

    fun add(text: String) = viewModelScope.launch { kitchen.addManual(text) }
    fun toggle(line: ShoppingLine) = viewModelScope.launch { kitchen.toggle(line) }
    fun remove(line: ShoppingLine) = viewModelScope.launch { line.manualId?.let { kitchen.removeManual(it) } }
    fun haveAtHome(line: ShoppingLine) = viewModelScope.launch { kitchen.haveAtHome(line, clock()) }
    fun checkoutToPantry() = viewModelScope.launch { kitchen.moveCheckedToPantry(state.value.lines, clock()) }
    fun addPantry(name: String) = viewModelScope.launch { kitchen.addPantry(name, clock()) }
    fun removePantry(name: String) = viewModelScope.launch { kitchen.removePantry(name) }
    fun addStaple(name: String) = viewModelScope.launch { kitchen.addStaple(name) }
    fun removeStaple(name: String) = viewModelScope.launch { kitchen.removeStaple(name) }
    fun shareText() = ShoppingPlanner.asText(state.value.lines, clock())
}
