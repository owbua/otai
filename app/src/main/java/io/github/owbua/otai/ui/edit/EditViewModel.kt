package io.github.owbua.otai.ui.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.data.model.Note
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.Step
import io.github.owbua.otai.data.model.StepTimer
import io.github.owbua.otai.data.model.Times
import io.github.owbua.otai.data.model.parseAmount
import io.github.owbua.otai.data.newRecipeId
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.web.RecipeEnricher
import io.github.owbua.otai.ui.feedback.editableNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Immutable
data class EditIngredient(
    val uid: String = UUID.randomUUID().toString(),
    val key: String? = null,
    val amount: String = "",
    val unit: String = "",
    val name: String = "",
    val note: String = "",
    val group: String? = null,
)

@Immutable
data class EditStep(
    val uid: String = UUID.randomUUID().toString(),
    val text: String = "",
    val ingredients: List<String> = emptyList(),
    val timerMin: String = "",
    val timerLabel: String? = null,
)

data class EditState(
    val loaded: Boolean = false,
    /** true = neues Rezept anlegen, false = vorhandenes bearbeiten */
    val isNew: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: String = "",
    val difficulty: String = "",
    val servings: Int = 2,
    val totalMin: String = "",
    val activeMin: String = "",
    val cookMin: String = "",
    val ingredients: List<EditIngredient> = emptyList(),
    val steps: List<EditStep> = emptyList(),
    val notes: List<Note> = emptyList(),
    val error: String? = null,
    /** ID des gespeicherten Rezepts – gesetzt, sobald gespeichert wurde. */
    val savedId: String? = null,
) {
    val valid get() = title.isNotBlank()
}

fun Recipe.toEditState() = EditState(
    loaded = true,
    title = title,
    description = description,
    category = category.orEmpty(),
    tags = tags.joinToString(", "),
    difficulty = difficulty.orEmpty(),
    servings = servings,
    totalMin = times.totalMin?.toString().orEmpty(),
    activeMin = times.activeMin?.toString().orEmpty(),
    cookMin = times.cookMin?.toString().orEmpty(),
    ingredients = ingredients.map {
        EditIngredient(key = it.key, amount = editableNumber(it.amount), unit = it.unit.orEmpty(), name = it.name, note = it.note.orEmpty(), group = it.group)
    },
    steps = steps.map {
        EditStep(text = it.text, ingredients = it.ingredients, timerMin = it.timer?.let { t -> editableNumber(t.seconds / 60.0) }.orEmpty(), timerLabel = it.timer?.label)
    },
    notes = notes,
)

/** Leerer Entwurf für „Neues Rezept“: je eine leere Zutat und ein leerer Schritt zum Loslegen. */
fun newRecipeDraft() = EditState(loaded = true, isNew = true, ingredients = listOf(EditIngredient()), steps = listOf(EditStep()))

fun EditState.applyTo(r: Recipe): Recipe {
    val keys = ingredients.mapNotNull { it.key }.toSet()
    return r.copy(
        title = title.trim(),
        description = description.trim(),
        category = category.trim().ifEmpty { null },
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        difficulty = difficulty.trim().ifEmpty { null },
        servings = servings,
        times = Times(totalMin.toIntOrNull(), activeMin.toIntOrNull(), cookMin.toIntOrNull()),
        ingredients = ingredients.filter { it.name.isNotBlank() }.map {
            Ingredient(
                key = it.key, amount = parseAmount(it.amount), unit = it.unit.trim().ifEmpty { null },
                name = it.name.trim(), note = it.note.trim().ifEmpty { null }, group = it.group,
            )
        },
        steps = steps.filter { it.text.isNotBlank() }.map { s ->
            val sec = parseAmount(s.timerMin)?.let { (it * 60).toInt() }?.takeIf { it > 0 }
            // Verweise auf gelöschte Zutaten entfernen
            Step(text = s.text.trim(), ingredients = s.ingredients.filter { it in keys }, timer = sec?.let { StepTimer(it, s.timerLabel) })
        },
        notes = notes,
    ).let { RecipeEnricher.enrich(it, timers = false) } // Zutaten-Bezüge der Schritte für die Kochansicht
}

/** @param id null = neues Rezept anlegen */
class EditViewModel(private val repo: RecipeRepository, private val id: String?) : ViewModel() {
    private val _state = MutableStateFlow(EditState())
    val state = _state.asStateFlow()
    private var initial: EditState? = null

    init {
        viewModelScope.launch {
            val s = if (id == null) newRecipeDraft() else repo.get(id)?.toEditState() ?: return@launch
            initial = s
            _state.value = s
        }
    }

    /** Ungespeicherte Änderungen vorhanden? */
    fun isDirty() = initial != null && _state.value.copy(error = null) != initial

    fun update(transform: (EditState) -> EditState) = _state.update { transform(it).copy(error = null) }

    fun updateIngredient(uid: String, t: (EditIngredient) -> EditIngredient) =
        update { s -> s.copy(ingredients = s.ingredients.map { if (it.uid == uid) t(it) else it }) }

    fun removeIngredient(uid: String) = update { s -> s.copy(ingredients = s.ingredients.filterNot { it.uid == uid }) }
    fun addIngredient() = update { s -> s.copy(ingredients = s.ingredients + EditIngredient()) }

    fun updateStep(uid: String, t: (EditStep) -> EditStep) =
        update { s -> s.copy(steps = s.steps.map { if (it.uid == uid) t(it) else it }) }

    fun removeStep(uid: String) = update { s -> s.copy(steps = s.steps.filterNot { it.uid == uid }) }
    fun addStep() = update { s -> s.copy(steps = s.steps + EditStep()) }
    fun removeNote(index: Int) = update { s -> s.copy(notes = s.notes.filterIndexed { i, _ -> i != index }) }

    fun save() = viewModelScope.launch {
        val s = _state.value
        if (!s.valid) { _state.update { it.copy(error = "Bitte einen Titel eingeben") }; return@launch }
        if (s.isNew) {
            val now = Instant.now().toString()
            val recipe = s.applyTo(Recipe(id = newRecipeId(s.title), createdAt = now))
            RecipeImporter.validate(recipe)?.let { err -> _state.update { it.copy(error = err) }; return@launch }
            val summary = repo.import(listOf(ImportedRecipe(recipe, null, "Neues Rezept")), ConflictMode.COPY)
            _state.update { it.copy(savedId = summary.ids.firstOrNull()) }
        } else {
            repo.update(id!!) { s.applyTo(it) }
            _state.update { it.copy(savedId = id) }
        }
    }
}
