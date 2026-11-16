package io.github.owbua.otai.domain

import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.PantryItem
import io.github.owbua.otai.data.model.Recipe
import java.time.LocalDate
import java.util.Locale

/** Eine Zeile der Einkaufsliste – abgeleitet aus dem Wochenplan oder selbst hinzugefügt. */
data class ShoppingLine(
    val key: String,
    val name: String,
    val amount: Double?,
    val unit: String?,
    val checked: Boolean,
    val fromPlan: Boolean,
    /** Rezepte, für die der Artikel gebraucht wird. */
    val recipes: List<String> = emptyList(),
    val manualId: String? = null,
) {
    fun amountText(): String = when {
        amount == null -> unit.orEmpty()
        unit.isNullOrBlank() -> formatNumber(amount)
        else -> "${formatNumber(amount, unit)} $unit"
    }
}

/** Vergleichs-Stamm für Zutatennamen: „Kartoffel(n)“, „Kartoffeln, festkochend“ → „kartoffel“. */
fun ingredientStem(name: String): String {
    var s = name.substringBefore(',').replace(Regex("\\(.*?\\)"), " ").lowercase(Locale.GERMAN)
        .replace(Regex("[^a-zäöüß ]"), " ").replace(Regex("\\s+"), " ").trim()
    if (s.isEmpty()) s = name.lowercase(Locale.GERMAN).trim()
    // einfache Pluralformen kappen: kartoffeln → kartoffel, tomaten → tomat, möhren → möhr
    return s.split(' ').joinToString(" ") { w ->
        when {
            w.length > 5 && w.endsWith("en") -> w.dropLast(2)
            w.length > 4 && (w.endsWith("n") || w.endsWith("e") || w.endsWith("s")) -> w.dropLast(1)
            else -> w
        }
    }
}

fun isStaple(name: String, staples: List<String>): Boolean {
    val stem = ingredientStem(name)
    return staples.any { ingredientStem(it) == stem }
}

fun inPantry(name: String, pantry: List<PantryItem>): Boolean {
    val stem = ingredientStem(name)
    return pantry.any { ingredientStem(it.name) == stem }
}

/** Zutaten mit „nach Belieben“, „optional“ o. Ä. sind kein Muss – etwa der Schmand zum Bauerntopf. */
private val OPTIONAL = Regex("""nach belieben|optional|n\. ?b\.|zum garnieren|zum servieren|wer mag""", RegexOption.IGNORE_CASE)

fun isOptional(ing: Ingredient): Boolean = OPTIONAL.containsMatchIn(ing.note.orEmpty()) || OPTIONAL.containsMatchIn(ing.name)

/**
 * Lässt sich das Rezept allein aus dem Vorhandenen kochen? Jede nötige Zutat muss im Vorrat stehen
 * oder eine Grundzutat sein (Salz, Öl …). Gleicher Namensabgleich wie bei der Einkaufsliste.
 */
fun cookableFromPantry(recipe: Recipe, kitchen: KitchenState): Boolean =
    recipe.ingredients.isNotEmpty() && missingFromPantry(recipe, kitchen).isEmpty()

/** Nötige Zutaten, die weder im Vorrat stehen noch Grundzutaten sind. */
fun missingFromPantry(recipe: Recipe, kitchen: KitchenState): List<Ingredient> =
    recipe.ingredients.filterNot { ing ->
        isOptional(ing) || isStaple(ing.name, kitchen.staples) || inPantry(ing.name, kitchen.pantry)
    }

object ShoppingPlanner {

    /** Geplante, noch nicht gekochte Einträge ab heute. */
    fun upcoming(state: KitchenState, today: LocalDate) =
        state.plan.filter { !it.cooked && runCatching { !LocalDate.parse(it.date).isBefore(today) }.getOrDefault(false) }

    fun build(state: KitchenState, recipes: List<Recipe>, today: LocalDate): List<ShoppingLine> {
        val byId = recipes.associateBy { it.id }
        data class Acc(val name: String, var amount: Double?, val unit: String?, val recipes: MutableList<String>, var anyAmount: Boolean)
        val acc = LinkedHashMap<String, Acc>()
        for (entry in upcoming(state, today).sortedBy { it.date }) {
            val recipe = byId[entry.recipeId] ?: continue
            val factor = entry.servings.toDouble() / recipe.servings
            for (ing in recipe.ingredients) {
                if (isStaple(ing.name, state.staples) || inPantry(ing.name, state.pantry)) continue
                val (amount, unit) = toBaseUnit(ing.amount?.times(factor), ing.unit)
                val key = "plan:${ingredientStem(ing.name)}|${unit.orEmpty().lowercase(Locale.GERMAN)}"
                val a = acc.getOrPut(key) { Acc(ing.name.substringBefore(',').trim(), null, unit, mutableListOf(), false) }
                if (amount != null) { a.amount = (a.amount ?: 0.0) + amount; a.anyAmount = true }
                if (recipe.title !in a.recipes) a.recipes += recipe.title
            }
        }
        val planLines = acc.map { (key, a) ->
            val (amount, unit) = fromBaseUnit(a.amount, a.unit)
            ShoppingLine(key, a.name, amount, unit, key in state.checkedPlanKeys, fromPlan = true, recipes = a.recipes)
        }
        val manualLines = state.manualItems.map {
            ShoppingLine("manual:${it.id}", it.name, it.amount, it.unit, it.checked, fromPlan = false, manualId = it.id)
        }
        val collator = java.text.Collator.getInstance(Locale.GERMAN)
        return (planLines + manualLines).sortedWith(compareBy<ShoppingLine> { it.checked }.thenComparator { a, b -> collator.compare(a.name, b.name) })
    }

    private fun toBaseUnit(amount: Double?, unit: String?): Pair<Double?, String?> = when (unit?.lowercase(Locale.GERMAN)) {
        "kg" -> amount?.times(1000) to "g"
        "l" -> amount?.times(1000) to "ml"
        else -> amount to unit
    }

    private fun fromBaseUnit(amount: Double?, unit: String?): Pair<Double?, String?> = when {
        amount != null && unit == "g" && amount >= 1000 -> amount / 1000 to "kg"
        amount != null && unit == "ml" && amount >= 1000 -> amount / 1000 to "l"
        else -> amount to unit
    }

    /** Einkaufsliste als Text zum Teilen (nur offene Artikel). */
    fun asText(lines: List<ShoppingLine>, date: LocalDate): String {
        val open = lines.filterNot { it.checked }
        val header = "Einkaufsliste vom ${date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
        return (listOf(header, "") + open.map { l ->
            val amount = l.amountText()
            "• " + if (amount.isBlank()) l.name else "$amount ${l.name}"
        }).joinToString("\n")
    }
}
