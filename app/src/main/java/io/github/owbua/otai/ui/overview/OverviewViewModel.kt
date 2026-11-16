package io.github.owbua.otai.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.domain.formatMinutes
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.owbua.otai.data.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.domain.missingFromPantry
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale

data class FilterChipUi(val id: String, val label: String, val selected: Boolean)

/** Schlankes, unveränderliches Anzeigemodell einer Rezeptkarte – Compose kann unveränderte Karten überspringen. */
@Immutable
data class RecipeCardUi(
    val id: String,
    val title: String,
    val time: String?,
    val meta: String,
    val favorite: Boolean,
    val photo: String?,
    /** Filter „Aus Vorrat“: was noch fehlt (null = nichts bzw. Filter aus) */
    val missing: String? = null,
)

fun Recipe.toCard(): RecipeCardUi {
    val time = formatMinutes(times.totalMin ?: times.activeMin)
    val cat = category ?: tags.firstOrNull()
    return RecipeCardUi(id, title, time, listOfNotNull(time, cat).joinToString(" · "), favorite, photo)
}

data class OverviewState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val query: String = "",
    val chips: List<FilterChipUi> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val cards: List<RecipeCardUi> = emptyList(),
    /** IDs der ausgewählten Rezepte (Mehrfachauswahl); leer = normaler Modus. */
    val selected: Set<String> = emptySet(),
    /** Eigener Leer-Hinweis für Filter, bei denen „Nichts gefunden“ nicht weiterhilft. */
    val emptyHint: Pair<String, String>? = null,
) {
    val selecting get() = selected.isNotEmpty()
}

const val FILTER_ALL = "alle"
const val FILTER_FAV = "fav"
const val FILTER_QUICK = "schnell"
const val FILTER_PANTRY = "vorrat"

fun buildOverview(all: List<Recipe>?, query: String, filter: String, kitchen: KitchenState? = null, pantryMaxMissing: Int = 0): OverviewState {
    if (all == null) return OverviewState(loading = true, query = query)
    val tagCounts = all.flatMap { r -> r.tags.map { it.lowercase(Locale.GERMAN) to it } }
        .groupBy({ it.first }, { it.second })
        .map { (_, labels) -> labels.first() to labels.size }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .take(5)
        .map { it.first }
    val ids = buildList {
        add(FILTER_ALL to "Alle")
        add(FILTER_FAV to "Favoriten")
        add(FILTER_QUICK to "≤ 30 Min")
        add(FILTER_PANTRY to "Aus Vorrat")
        tagCounts.forEach { add("tag:${it.lowercase(Locale.GERMAN)}" to it.replaceFirstChar { c -> c.titlecase(Locale.GERMAN) }) }
    }
    val effectiveFilter = if (ids.any { it.first == filter }) filter else FILTER_ALL
    val q = query.trim().lowercase(Locale.GERMAN)
    val collator = Collator.getInstance(Locale.GERMAN)
    val list = all.filter { r ->
        when {
            effectiveFilter == FILTER_FAV -> r.favorite
            effectiveFilter == FILTER_QUICK -> (r.times.totalMin ?: r.times.activeMin ?: Int.MAX_VALUE) <= 30
            effectiveFilter == FILTER_PANTRY -> kitchen != null && r.ingredients.isNotEmpty() &&
                missingFromPantry(r, kitchen).size <= pantryMaxMissing
            effectiveFilter.startsWith("tag:") -> r.tags.any { it.lowercase(Locale.GERMAN) == effectiveFilter.removePrefix("tag:") }
            else -> true
        }
    }.filter { r ->
        q.isEmpty() || buildString {
            append(r.title).append(' ').append(r.description).append(' ').append(r.category.orEmpty()).append(' ')
            r.tags.forEach { append(it).append(' ') }
            r.ingredients.forEach { append(it.name).append(' ') }
        }.lowercase(Locale.GERMAN).contains(q)
    }.sortedWith { a, b -> collator.compare(a.title, b.title) }
        .let { sorted ->
            // „Aus Vorrat“ mit Spielraum: erst, wofür alles da ist, dann nach Zahl der fehlenden Zutaten
            if (effectiveFilter == FILTER_PANTRY && kitchen != null && pantryMaxMissing > 0) sorted.sortedBy { missingFromPantry(it, kitchen).size } else sorted
        }
    return OverviewState(
        loading = false,
        totalCount = all.size,
        query = query,
        chips = ids.map { (id, label) -> FilterChipUi(id, label, id == effectiveFilter) },
        recipes = list,
        cards = list.map { r ->
            val missing = if (effectiveFilter == FILTER_PANTRY && kitchen != null) missingFromPantry(r, kitchen) else emptyList()
            r.toCard().copy(missing = missing.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name.substringBefore(',').trim() })
        },
        emptyHint = if (effectiveFilter == FILTER_PANTRY && list.isEmpty() && q.isEmpty()) {
            if (kitchen?.pantry.isNullOrEmpty()) {
                "Vorrat ist leer" to "Trag unter Einkauf → Vorrat ein, was du zuhause hast. Dann erscheinen hier die Rezepte, für die alles da ist."
            } else {
                "Noch nicht alles da" to (
                    (if (pantryMaxMissing == 0) "Für kein Rezept ist jede Zutat im Vorrat. In den Einstellungen kannst du erlauben, dass ein paar Zutaten fehlen dürfen. "
                    else "Bei keinem Rezept fehlen höchstens $pantryMaxMissing Zutaten. ") +
                        "Grundzutaten wie Salz, Pfeffer und Öl sowie Zutaten „nach Belieben“ zählen nicht mit."
                )
            }
        } else null,
    )
}

class OverviewViewModel(
    private val repo: RecipeRepository,
    private val kitchen: KitchenRepository? = null,
    private val settings: kotlinx.coroutines.flow.Flow<Int> = flowOf(0),
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(FILTER_ALL)
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    private val pantry = combine(kitchen?.state ?: flowOf(null), settings) { k, max -> k to max }

    val state: StateFlow<OverviewState> = combine(repo.recipes, query, filter, selection, pantry) { all, q, f, sel, (k, max) ->
        val existing = all?.mapTo(HashSet()) { it.id }.orEmpty()
        buildOverview(all, q, f, k, max).copy(selected = sel intersect existing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(id: String) { filter.value = id }

    // --- Mehrfachauswahl ---------------------------------------------------------------------------
    fun toggleSelection(id: String) = selection.update { if (id in it) it - id else it + id }
    fun selectAllVisible() = selection.update { it + state.value.recipes.map { r -> r.id } }
    fun clearSelection() { selection.value = emptySet() }
    fun selectedIds(): List<String> = state.value.selected.toList()

    /** Sind alle ausgewählten schon Favoriten → entfernen, sonst alle zu Favoriten machen. */
    fun toggleFavoriteSelected() = viewModelScope.launch {
        val ids = state.value.selected
        val all = repo.all().filter { it.id in ids }
        val makeFav = !all.all { it.favorite }
        all.forEach { r -> repo.update(r.id) { it.copy(favorite = makeFav) } }
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = state.value.selected.toList()
        clearSelection()
        ids.forEach { id ->
            repo.delete(id)
            kitchen?.onRecipeDeleted(id)
        }
    }
}
