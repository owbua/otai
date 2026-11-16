package io.github.owbua.otai.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeUiState(
    val loading: Boolean = true,
    val recipe: Recipe? = null,
    val servings: Int = 2,
    val checked: Set<String> = emptySet(),
)

class RecipeViewModel(
    private val repo: RecipeRepository,
    private val id: String,
    private val kitchen: KitchenRepository? = null,
) : ViewModel() {
    private val servings = MutableStateFlow<Int?>(null)
    private val checked = MutableStateFlow<Set<String>>(emptySet())

    // Schon geladenes Rezept sofort zeigen: Das Titelbild muss im ersten Frame stehen, damit die
    // Kachel aus der Übersicht hineinfliegen kann.
    private val initial = repo.recipes.value?.firstOrNull { it.id == id }

    val state: StateFlow<RecipeUiState> = combine(repo.recipe(id), servings, checked) { r, s, ch ->
        RecipeUiState(loading = false, recipe = r, servings = s ?: r?.servings ?: 2, checked = ch)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        if (initial != null) RecipeUiState(loading = false, recipe = initial, servings = initial.servings) else RecipeUiState(),
    )

    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()

    fun less() = servings.update { ((it ?: state.value.servings) - 1).coerceAtLeast(1) }
    fun more() = servings.update { ((it ?: state.value.servings) + 1).coerceAtMost(99) }

    fun toggleIngredient(key: String) = checked.update { if (key in it) it - key else it + key }

    fun toggleFavorite() = viewModelScope.launch { repo.update(id) { it.copy(favorite = !it.favorite) } }

    fun addToPlan(date: java.time.LocalDate, servings: Int) = viewModelScope.launch { kitchen?.addToPlan(date, id, servings) }

    fun setPhoto(jpeg: ByteArray) = viewModelScope.launch { repo.setPhoto(id, jpeg) }

    fun removePhoto() = viewModelScope.launch { repo.removePhoto(id) }

    fun delete() = viewModelScope.launch {
        repo.delete(id)
        kitchen?.onRecipeDeleted(id)
        _deleted.value = true
    }
}
