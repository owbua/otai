package io.github.owbua.otai.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.parseAmount
import io.github.owbua.otai.domain.applyFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

data class AmountField(val key: String, val name: String, val unit: String?, val note: String?, val initial: String, val text: String) {
    val changed get() = text.trim() != initial
}

data class FeedbackState(
    val recipe: Recipe? = null,
    val servings: Int = 2,
    val rating: Int? = null,
    val note: String = "",
    val amounts: List<AmountField> = emptyList(),
    val saved: Boolean = false,
)

/** Zahl für ein Eingabefeld: max. 2 Nachkommastellen, deutsches Komma, ohne ,0. */
fun editableNumber(v: Double?): String {
    if (v == null) return ""
    val r = (v * 100).roundToLong() / 100.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
}

/** Sinnvolle Schrittweite für ± je nach Einheit und Größe. */
fun stepFor(value: Double, unit: String?): Double {
    val u = unit?.lowercase(Locale.GERMAN)
    return when (u) {
        "g", "ml" -> when { value >= 500 -> 50.0; value >= 100 -> 25.0; value >= 20 -> 5.0; else -> 1.0 }
        "kg", "l" -> 0.1
        else -> if (value < 3) 0.5 else 1.0
    }
}

class FeedbackViewModel(
    private val repo: RecipeRepository,
    private val id: String,
    servings: Int,
    private val kitchen: KitchenRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedbackState(servings = servings))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = repo.get(id) ?: return@launch
            val factor = servings.toDouble() / r.servings
            _state.update {
                it.copy(
                    recipe = r,
                    amounts = r.ingredients.mapNotNull { ing ->
                        val key = ing.key ?: return@mapNotNull null
                        val init = editableNumber(ing.amount?.times(factor))
                        AmountField(key, ing.name, ing.unit, ing.note, init, init)
                    },
                )
            }
        }
    }

    fun setRating(r: Int) = _state.update { it.copy(rating = if (it.rating == r) null else r) }
    fun setNote(t: String) = _state.update { it.copy(note = t) }

    fun setAmount(key: String, text: String) = _state.update { s ->
        s.copy(amounts = s.amounts.map { if (it.key == key) it.copy(text = text.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '/' || c == ' ' }) else it })
    }

    fun nudge(key: String, direction: Int) = _state.update { s ->
        s.copy(amounts = s.amounts.map { f ->
            if (f.key != key) return@map f
            val cur = parseAmount(f.text) ?: 0.0
            val next = (cur + direction * stepFor(cur + if (direction < 0) -0.001 else 0.0, f.unit)).coerceAtLeast(0.0)
            f.copy(text = if (next <= 0.0) "" else editableNumber(next))
        })
    }

    fun save() = viewModelScope.launch {
        val s = _state.value
        val changed = s.amounts.filter { it.changed }.associate { it.key to parseAmount(it.text) }
        repo.update(id) {
            it.applyFeedback(s.servings, changed, s.note, s.rating, LocalDate.now().toString())
        }
        markCooked()
        _state.update { it.copy(saved = true) }
    }

    /** „Überspringen“ zählt trotzdem als gekocht. */
    fun skip() = viewModelScope.launch {
        repo.update(id) { it.applyFeedback(_state.value.servings, emptyMap(), "", null, LocalDate.now().toString()) }
        markCooked()
        _state.update { it.copy(saved = true) }
    }

    private suspend fun markCooked() {
        val r = repo.get(id) ?: return
        kitchen?.markCooked(r, LocalDate.now())
    }
}
