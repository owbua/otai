package io.github.owbua.otai.ui.cook

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.model.Recipe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Zustand eines Schritt-Timers. Läuft er, ist [endAt] gesetzt (monotone Uhr, ms). */
data class StepTimerState(val totalMs: Long, val remainingMs: Long, val endAt: Long? = null) {
    val running get() = endAt != null
    fun remainingAt(now: Long) = if (endAt != null) (endAt - now).coerceAtLeast(0) else remainingMs
}

data class CookUiState(
    val recipe: Recipe? = null,
    val servings: Int = 2,
    val step: Int = 0,
    val timers: Map<Int, StepTimerState> = emptyMap(),
    val now: Long = 0,
)

class CookViewModel(
    repo: RecipeRepository,
    recipeId: String,
    servings: Int,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val onTimerFinished: (recipeTitle: String, label: String) -> Unit = { _, _ -> },
) : ViewModel() {

    private val step = MutableStateFlow(saved.get<Int>("step") ?: 0)
    private val timers = MutableStateFlow<Map<Int, StepTimerState>>(emptyMap())
    private val now = MutableStateFlow(clock())
    private var ticker: Job? = null
    private var recipeCache: Recipe? = null

    val state: StateFlow<CookUiState> = combine(repo.recipe(recipeId), step, timers, now) { r, s, t, n ->
        recipeCache = r
        CookUiState(r, servings, s.coerceIn(0, ((r?.steps?.size ?: 1) - 1).coerceAtLeast(0)), t, n)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CookUiState(servings = servings))

    fun next() = setStep(step.value + 1)
    fun prev() = setStep(step.value - 1)

    fun setStep(i: Int) {
        val max = (recipeCache?.steps?.size ?: 1) - 1
        step.value = i.coerceIn(0, max.coerceAtLeast(0))
        saved["step"] = step.value
    }

    fun toggleTimer(stepIndex: Int) {
        val recipe = recipeCache ?: return
        val def = recipe.steps.getOrNull(stepIndex)?.timer ?: return
        val t = clock()
        timers.update { map ->
            val cur = map[stepIndex] ?: StepTimerState(def.seconds * 1000L, def.seconds * 1000L)
            val next = when {
                cur.running -> cur.copy(remainingMs = cur.remainingAt(t), endAt = null)
                cur.remainingMs <= 0 -> cur.copy(remainingMs = cur.totalMs, endAt = t + cur.totalMs)
                else -> cur.copy(endAt = t + cur.remainingMs)
            }
            map + (stepIndex to next)
        }
        ensureTicker()
    }

    fun resetTimer(stepIndex: Int) {
        timers.update { map ->
            val cur = map[stepIndex] ?: return@update map
            map + (stepIndex to cur.copy(remainingMs = cur.totalMs, endAt = null))
        }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                val t = clock()
                now.value = t
                val finished = timers.value.filter { (_, s) -> s.endAt != null && s.endAt <= t }
                if (finished.isNotEmpty()) {
                    timers.update { map -> map + finished.mapValues { (_, s) -> s.copy(remainingMs = 0, endAt = null) } }
                    val r = recipeCache
                    finished.keys.forEach { idx ->
                        val label = r?.steps?.getOrNull(idx)?.timer?.label ?: "Schritt ${idx + 1}"
                        onTimerFinished(r?.title.orEmpty(), label)
                    }
                }
                if (timers.value.values.none { it.running }) break
                delay(200)
            }
        }
    }
}
