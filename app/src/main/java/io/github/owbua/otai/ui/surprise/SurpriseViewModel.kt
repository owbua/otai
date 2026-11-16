package io.github.owbua.otai.ui.surprise

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.SettingsRepository
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.ShoppingItem
import io.github.owbua.otai.domain.EmptyPantryException
import io.github.owbua.otai.domain.SurpriseFinder
import io.github.owbua.otai.domain.SurpriseHit
import io.github.owbua.otai.domain.SurpriseProgress
import io.github.owbua.otai.domain.amountText
import io.github.owbua.otai.domain.formatMinutes
import io.github.owbua.otai.io.web.WebRecipe
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class HitIngredient(val text: String, val missing: Boolean)

@Immutable
data class HitUi(
    val url: String,
    val title: String,
    val imageUrl: String?,
    val meta: String,
    val missing: List<String>,
    val ingredients: List<HitIngredient>,
    /** ID im Kochbuch, falls das Rezept schon importiert wurde */
    val existingId: String? = null,
    val onList: Boolean = false,
)

sealed interface SurprisePhase {
    data object Idle : SurprisePhase
    data class Loading(val progress: SurpriseProgress?) : SurprisePhase
    data object Done : SurprisePhase
    data object EmptyPantry : SurprisePhase
    data class Failed(val message: String) : SurprisePhase
}

data class SurpriseState(
    val phase: SurprisePhase = SurprisePhase.Idle,
    val hits: List<HitUi> = emptyList(),
    val terms: List<String> = emptyList(),
    val checked: Int = 0,
    val maxMissing: Int = 1,
    val usePantry: Boolean = true,
    val minResults: Int = 5,
)

/** Chefkoch-Rezeptnummer – bleibt gleich, auch wenn sich der Titel in der Adresse ändert. */
fun chefkochId(url: String?): String? = url?.let { Regex("""/rezepte/(\d+)/""").find(it)?.groupValues?.get(1) }

fun SurpriseHit.toUi(existing: List<Recipe>): HitUi {
    val r = web.recipe
    val missingSet = missing.toSet()
    val id = chefkochId(url)
    return HitUi(
        url = url,
        title = r.title,
        imageUrl = web.imageUrl,
        // wie in der Übersicht: Zeit · Kategorie
        meta = listOfNotNull(formatMinutes(r.times.totalMin ?: r.times.activeMin), r.category ?: r.tags.firstOrNull()).joinToString(" · "),
        missing = missing.map { it.name.substringBefore(',').trim() },
        ingredients = r.ingredients.map { ing ->
            HitIngredient(listOf(ing.amountText(), ing.name).filter { it.isNotBlank() }.joinToString(" "), ing in missingSet)
        },
        existingId = existing.firstOrNull { e -> id != null && chefkochId(e.source?.url) == id || e.source?.url == url }?.id,
    )
}

class SurpriseViewModel(
    private val finder: SurpriseFinder,
    private val kitchen: KitchenRepository,
    private val repo: RecipeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(settings.state.value.let { SurpriseState(maxMissing = it.surpriseMaxMissing, usePantry = it.surpriseUsePantry, minResults = it.surpriseMinResults) })
    val state: StateFlow<SurpriseState> = _state.asStateFlow()

    /** Schon gezeigte Rezepte – beim Neu-Würfeln nicht noch einmal. */
    private val seen = mutableSetOf<String>()
    private val webByUrl = mutableMapOf<String, Pair<WebRecipe, SurpriseHit>>()
    private var job: Job? = null

    init {
        roll()
        // Strenge in den Einstellungen geändert → gleich mit der neuen suchen
        viewModelScope.launch {
            settings.state.map { Triple(it.surpriseMaxMissing, it.surpriseUsePantry, it.surpriseMinResults) }.distinctUntilChanged().drop(1).collect {
                seen.clear()
                roll()
            }
        }
        // „Schon im Kochbuch“ aktuell halten (z. B. nach dem Import)
        viewModelScope.launch {
            repo.recipes.collect { all ->
                val list = all.orEmpty()
                _state.update { s -> s.copy(hits = s.hits.map { h -> h.copy(existingId = webByUrl[h.url]?.second?.toUi(list)?.existingId) }) }
            }
        }
    }

    fun roll() {
        job?.cancel()
        val cfg = settings.state.value
        val maxMissing = cfg.surpriseMaxMissing
        _state.update {
            it.copy(phase = SurprisePhase.Loading(null), maxMissing = maxMissing, usePantry = cfg.surpriseUsePantry, minResults = cfg.surpriseMinResults)
        }
        job = viewModelScope.launch {
            val k = kitchen.state.filterNotNull().first()
            try {
                var result = finder.find(k, maxMissing, seen, usePantry = cfg.surpriseUsePantry, minResults = cfg.surpriseMinResults) { p -> _state.update { it.copy(phase = SurprisePhase.Loading(p)) } }
                if (result.hits.isEmpty() && result.checked == 0 && seen.isNotEmpty()) {
                    // alles schon gezeigt → von vorn
                    seen.clear()
                    result = finder.find(k, maxMissing, seen, usePantry = cfg.surpriseUsePantry, minResults = cfg.surpriseMinResults) { p -> _state.update { it.copy(phase = SurprisePhase.Loading(p)) } }
                }
                val existing = repo.recipes.value.orEmpty()
                webByUrl.clear()
                result.hits.forEach { webByUrl[it.url] = it.web to it }
                _state.update {
                    it.copy(phase = SurprisePhase.Done, hits = result.hits.map { h -> h.toUi(existing) }, terms = result.terms, checked = result.checked)
                }
            } catch (e: EmptyPantryException) {
                _state.update { it.copy(phase = SurprisePhase.EmptyPantry, hits = emptyList()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = SurprisePhase.Failed(errorText(e))) }
            }
        }
    }

    /** Aus dem Vorrat ↔ beliebig – wird gemerkt; die Beobachtung oben würfelt neu. */
    fun setUsePantry(on: Boolean) = settings.setSurpriseUsePantry(on)

    fun web(url: String): WebRecipe? = webByUrl[url]?.first

    /** Fehlende Zutaten auf die Einkaufsliste */
    fun addMissing(url: String) = viewModelScope.launch {
        val hit = webByUrl[url]?.second ?: return@launch
        kitchen.addManualItems(hit.missing.map { ShoppingItem(UUID.randomUUID().toString(), it.name.substringBefore(',').trim(), it.amount, it.unit) })
        _state.update { s -> s.copy(hits = s.hits.map { if (it.url == url) it.copy(onList = true) else it }) }
    }

    private fun errorText(e: Exception) = when (e) {
        is java.net.UnknownHostException -> "Keine Internetverbindung"
        is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> "Chefkoch antwortet gerade nicht (Zeitüberschreitung)"
        else -> "Suche fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
    }
}
