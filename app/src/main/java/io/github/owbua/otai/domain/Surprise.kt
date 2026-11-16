package io.github.owbua.otai.domain

import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.io.web.ChefkochSearch
import io.github.owbua.otai.io.web.JsonLdRecipeParser
import io.github.owbua.otai.io.web.WebRecipe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/** Ein Fund: Rezept von Chefkoch und was dafür noch fehlt. */
class SurpriseHit(val url: String, val web: WebRecipe, val missing: List<Ingredient>)

class SurpriseResult(val hits: List<SurpriseHit>, val terms: List<String>, val checked: Int)

data class SurpriseProgress(val terms: List<String>, val checked: Int, val total: Int)

class EmptyPantryException : Exception("Der Vorrat ist leer")

/**
 * „Überrasch mich“: sucht bei Chefkoch nach zufällig gewählten Zutaten aus dem Vorrat, lädt einige
 * Treffer und behält die, bei denen höchstens [maxMissing] nötige Zutaten fehlen.
 *
 * Laden ist injizierbar: [loadDirect] = schlichter HTTP-Abruf, [loadBrowser] = unsichtbare WebView als
 * Rückfall bei Bot-Schutz. Blockt Chefkoch den direkten Abruf einmal, geht es gleich über die WebView
 * weiter, statt jede Seite doppelt anzufragen.
 */
class SurpriseFinder(
    private val loadDirect: suspend (String) -> String,
    private val loadBrowser: suspend (String) -> String,
    private val parse: (html: String, url: String) -> WebRecipe = JsonLdRecipeParser::parse,
) {
    @Volatile private var browserOnly = false

    /**
     * @param usePantry true = mit Zutaten aus dem Vorrat suchen und nach [maxMissing] filtern;
     *                  false = beliebige Rezepte (Suchbegriffe aus [ANY_TERMS], kein Filter).
     * @param minResults so viele Funde anstreben; die Zahl geprüfter Seiten wächst mit.
     */
    suspend fun find(
        kitchen: KitchenState,
        maxMissing: Int,
        seen: MutableSet<String>,
        random: Random = Random.Default,
        usePantry: Boolean = true,
        minResults: Int = DEFAULT_MIN_RESULTS,
        onProgress: (SurpriseProgress) -> Unit = {},
    ): SurpriseResult {
        val pool = if (usePantry) searchPool(kitchen) else ANY_TERMS
        if (pool.isEmpty()) throw EmptyPantryException()
        val target = minResults.coerceAtLeast(1)
        val maxChecks = maxChecksFor(target, usePantry)
        val hits = mutableListOf<SurpriseHit>()
        val tried = mutableSetOf<List<String>>()
        var checked = 0
        var lastTerms = emptyList<String>()
        var searchError: Exception? = null
        for (round in 0 until MAX_ROUNDS) {
            if (hits.size >= target || checked >= maxChecks) break
            // Vorrat: erst zwei Zutaten kombinieren, danach breiter mit je einer suchen
            val terms = pickTerms(pool, if (usePantry && round == 0) 2 else 1, tried, random) ?: break
            tried += terms
            lastTerms = terms
            onProgress(SurpriseProgress(terms, checked, 0))
            val links = try {
                searchLinks(ChefkochSearch.searchUrl(terms))
            } catch (e: Exception) {
                searchError = e
                continue
            }
            val perRound = if (usePantry) maxChecks - checked else (target - hits.size)
            val candidates = links.filter { it !in seen }.shuffled(random).take(perRound.coerceAtMost(maxChecks - checked))
            val total = checked + candidates.size
            for (batch in candidates.chunked(PARALLEL)) {
                val loaded = coroutineScope { batch.map { url -> async { url to loadRecipe(url) } }.awaitAll() }
                for ((url, web) in loaded) {
                    seen += url
                    checked++
                    if (web == null || web.recipe.ingredients.isEmpty()) continue
                    val missing = missingFromPantry(web.recipe, kitchen)
                    if (!usePantry || missing.size <= maxMissing) hits += SurpriseHit(url, web, missing)
                }
                onProgress(SurpriseProgress(terms, checked, total))
                if (hits.size >= target) break
            }
        }
        // Nichts geprüft und die Suche schlug fehl → Fehler melden statt „nichts gefunden“
        if (checked == 0 && searchError != null) throw searchError
        return SurpriseResult(if (usePantry) hits.sortedBy { it.missing.size } else hits, lastTerms, checked)
    }

    private suspend fun searchLinks(url: String): List<String> {
        if (!browserOnly) {
            val direct = runCatching { ChefkochSearch.recipeLinks(loadDirect(url)) }
            if (direct.isFailure) browserOnly = true
            direct.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ChefkochSearch.recipeLinks(loadBrowser(url))
    }

    private suspend fun loadRecipe(url: String): WebRecipe? {
        if (!browserOnly) {
            val direct = runCatching { loadDirect(url) }
            if (direct.isFailure) browserOnly = true
            direct.getOrNull()?.let { html -> runCatching { parse(html, url) }.getOrNull() }?.let { return it }
        }
        return runCatching { parse(loadBrowser(url), url) }.getOrNull()
    }

    companion object {
        const val DEFAULT_MIN_RESULTS = 5
        const val MAX_ROUNDS = 6

        /**
         * Höchstens so viele Rezeptseiten pro Durchgang – schont Chefkoch und dauert nicht ewig.
         * Mit Vorrat fällt ein Teil der Treffer durch, daher mehr prüfen (3 je gewünschtem Fund, 12–30).
         */
        fun maxChecksFor(minResults: Int, usePantry: Boolean) =
            if (usePantry) (minResults * 3).coerceIn(12, 30) else minResults.coerceAtMost(30)

        /** Suchbegriffe für „Beliebig“: Grundzutaten und Gerichtarten quer durch die Küche. */
        val ANY_TERMS = listOf(
            "Nudeln", "Hähnchen", "Reis", "Kartoffeln", "Auflauf", "Suppe", "Eintopf", "Curry", "Salat", "Lachs",
            "Linsen", "Pfanne", "Risotto", "Gulasch", "Quiche", "Wok", "Ofengemüse", "Kichererbsen", "Tofu",
            "Hackfleisch", "Pilze", "Spinat", "Kürbis", "Gnocchi", "Schnitzel", "Lasagne", "Ramen", "Teriyaki",
            "Flammkuchen", "Bowl", "Chili", "Frikadellen", "Couscous", "Paprika", "Zucchini", "Pute", "Garnelen",
        )
        const val PARALLEL = 3

        /** Suchbegriffe: Vorrat ohne Grundzutaten, je Wortstamm einmal. */
        fun searchPool(kitchen: KitchenState): List<String> =
            kitchen.pantry.map { it.name.trim() }
                .filter { it.isNotEmpty() && !isStaple(it, kitchen.staples) }
                .distinctBy { ingredientStem(it) }

        fun pickTerms(pool: List<String>, count: Int, tried: Set<List<String>>, random: Random): List<String>? {
            repeat(20) {
                val terms = pool.shuffled(random).take(count.coerceAtMost(pool.size)).sorted()
                if (terms !in tried) return terms
            }
            return null
        }
    }
}
