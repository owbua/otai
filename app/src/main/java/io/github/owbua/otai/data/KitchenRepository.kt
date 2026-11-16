package io.github.owbua.otai.data

import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.PantryItem
import io.github.owbua.otai.data.model.PlanEntry
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.ShoppingItem
import io.github.owbua.otai.domain.ShoppingLine
import io.github.owbua.otai.domain.ingredientStem
import io.github.owbua.otai.domain.isStaple
import io.github.owbua.otai.io.web.IngredientParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

/** Wochenplan, Einkaufsliste und Vorrat – eine Datei `kitchen.json` im App-Speicher. */
class KitchenRepository(
    baseDir: File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val file = File(baseDir, "kitchen.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<KitchenState?>(null)
    val state: StateFlow<KitchenState?> = _state.asStateFlow()

    init {
        scope.launch(io) {
            _state.value = runCatching { RecipeJson.json.decodeFromString(KitchenState.serializer(), file.readText()) }
                .getOrDefault(KitchenState())
        }
    }

    suspend fun current(): KitchenState = state.filterNotNull().first()

    suspend fun update(transform: (KitchenState) -> KitchenState) = mutex.withLock {
        val next = transform(current())
        withContext(io) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(RecipeJson.json.encodeToString(KitchenState.serializer(), next))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
        _state.value = next
    }

    // --- Wochenplan ------------------------------------------------------------------------------

    suspend fun addToPlan(date: LocalDate, recipeId: String, servings: Int, reason: String? = null) = update {
        it.copy(plan = it.plan + PlanEntry(UUID.randomUUID().toString(), date.toString(), recipeId, servings.coerceIn(1, 99), reason = reason))
    }

    suspend fun replaceInPlan(entryId: String, recipeId: String, servings: Int, reason: String?) = update { s ->
        s.copy(plan = s.plan.map { if (it.id == entryId) it.copy(recipeId = recipeId, servings = servings, reason = reason, cooked = false) else it })
    }

    suspend fun removeFromPlan(entryId: String) = update { s -> s.copy(plan = s.plan.filterNot { it.id == entryId }) }

    suspend fun setPlanServings(entryId: String, servings: Int) = update { s ->
        s.copy(plan = s.plan.map { if (it.id == entryId) it.copy(servings = servings.coerceIn(1, 99)) else it })
    }

    suspend fun setPlanCooked(entryId: String, cooked: Boolean) = update { s ->
        s.copy(plan = s.plan.map { if (it.id == entryId) it.copy(cooked = cooked) else it })
    }

    /**
     * Nach dem Kochen: passenden Plan-Eintrag (±2 Tage) als gekocht markieren und die verbrauchten
     * Zutaten aus dem Vorrat nehmen (Grundzutaten bleiben).
     */
    suspend fun markCooked(recipe: Recipe, today: LocalDate) = update { s ->
        val entry = s.plan
            .filter { it.recipeId == recipe.id && !it.cooked }
            .mapNotNull { e -> runCatching { e to abs(ChronoUnit.DAYS.between(LocalDate.parse(e.date), today)) }.getOrNull() }
            .filter { it.second <= 2 }
            .minByOrNull { it.second }?.first
        val used = recipe.ingredients.filterNot { isStaple(it.name, s.staples) }.map { ingredientStem(it.name) }.toSet()
        s.copy(
            plan = s.plan.map { if (it.id == entry?.id) it.copy(cooked = true) else it },
            pantry = s.pantry.filterNot { ingredientStem(it.name) in used },
        )
    }

    suspend fun onRecipeDeleted(recipeId: String) = update { s -> s.copy(plan = s.plan.filterNot { it.recipeId == recipeId }) }

    // --- Einkaufsliste ---------------------------------------------------------------------------

    /** „500 g Mehl“ → Menge/Einheit/Name. */
    suspend fun addManual(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val parsed = IngredientParser.parse(t)
        update { s ->
            s.copy(manualItems = s.manualItems + ShoppingItem(UUID.randomUUID().toString(), parsed.name.ifBlank { t }, parsed.amount, parsed.unit))
        }
    }

    /** Mehrere Artikel auf einmal (z. B. fehlende Zutaten); schon offene mit gleichem Namen nicht doppelt. */
    suspend fun addManualItems(items: List<ShoppingItem>) = update { s ->
        val open = s.manualItems.filter { !it.checked }.mapTo(HashSet()) { ingredientStem(it.name) }
        val fresh = items.filter { open.add(ingredientStem(it.name)) }
        s.copy(manualItems = s.manualItems + fresh)
    }

    suspend fun toggle(line: ShoppingLine) = update { s ->
        if (line.manualId != null) {
            s.copy(manualItems = s.manualItems.map { if (it.id == line.manualId) it.copy(checked = !it.checked) else it })
        } else {
            s.copy(checkedPlanKeys = if (line.key in s.checkedPlanKeys) s.checkedPlanKeys - line.key else s.checkedPlanKeys + line.key)
        }
    }

    suspend fun removeManual(id: String) = update { s -> s.copy(manualItems = s.manualItems.filterNot { it.id == id }) }

    /** Abgehakte Artikel gelten als gekauft: ab in den Vorrat, raus aus der Liste. */
    suspend fun moveCheckedToPantry(lines: List<ShoppingLine>, today: LocalDate) = update { s ->
        val bought = lines.filter { it.checked }
        val pantryStems = s.pantry.map { ingredientStem(it.name) }.toSet()
        val newPantry = bought.map { it.name }.distinctBy { ingredientStem(it) }
            .filterNot { ingredientStem(it) in pantryStems }
            .map { PantryItem(it, today.toString()) }
        s.copy(
            pantry = s.pantry + newPantry,
            manualItems = s.manualItems.filterNot { it.checked },
            checkedPlanKeys = emptySet(),
        )
    }

    /** „Habe ich schon“: Artikel direkt in den Vorrat. */
    suspend fun haveAtHome(line: ShoppingLine, today: LocalDate) = update { s ->
        val stem = ingredientStem(line.name)
        s.copy(
            pantry = if (s.pantry.any { ingredientStem(it.name) == stem }) s.pantry else s.pantry + PantryItem(line.name, today.toString()),
            manualItems = if (line.manualId != null) s.manualItems.filterNot { it.id == line.manualId } else s.manualItems,
            checkedPlanKeys = s.checkedPlanKeys - line.key,
        )
    }

    // --- Vorrat & Grundzutaten -------------------------------------------------------------------

    suspend fun addPantry(name: String, today: LocalDate) {
        val n = name.trim().ifEmpty { return }
        update { s -> if (s.pantry.any { ingredientStem(it.name) == ingredientStem(n) }) s else s.copy(pantry = s.pantry + PantryItem(n, today.toString())) }
    }

    suspend fun removePantry(name: String) = update { s -> s.copy(pantry = s.pantry.filterNot { it.name == name }) }

    suspend fun addStaple(name: String) {
        val n = name.trim().ifEmpty { return }
        update { s -> if (s.staples.any { ingredientStem(it) == ingredientStem(n) }) s else s.copy(staples = s.staples + n) }
    }

    suspend fun removeStaple(name: String) = update { s -> s.copy(staples = s.staples - name) }

    // --- Backup ----------------------------------------------------------------------------------

    /** Backup zusammenführen statt überschreiben – nichts Vorhandenes geht verloren. */
    suspend fun merge(other: KitchenState, knownRecipeIds: Set<String>) = update { s ->
        val planKeys = s.plan.map { it.date + "|" + it.recipeId }.toSet()
        s.copy(
            plan = s.plan + other.plan.filter { it.recipeId in knownRecipeIds && (it.date + "|" + it.recipeId) !in planKeys }
                .map { it.copy(id = UUID.randomUUID().toString()) },
            manualItems = s.manualItems + other.manualItems.filterNot { o -> s.manualItems.any { it.name.equals(o.name, true) } },
            pantry = s.pantry + other.pantry.filterNot { o -> s.pantry.any { ingredientStem(it.name) == ingredientStem(o.name) } },
            staples = s.staples + other.staples.filterNot { o -> s.staples.any { ingredientStem(it) == ingredientStem(o) } },
        )
    }
}
