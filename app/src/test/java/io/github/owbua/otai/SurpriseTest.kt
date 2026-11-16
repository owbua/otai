package io.github.owbua.otai

import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.PantryItem
import io.github.owbua.otai.domain.EmptyPantryException
import io.github.owbua.otai.domain.SurpriseFinder
import io.github.owbua.otai.io.web.ChefkochSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class SurpriseTest {

    private fun page(title: String, vararg ingredients: String) = """
        <html><head><script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"$title","recipeYield":"2 Portionen",
         "recipeIngredient":[${ingredients.joinToString(",") { "\"$it\"" }}],
         "recipeInstructions":"Alles zubereiten."}
        </script></head><body></body></html>
    """.trimIndent()

    private val pages = mapOf(
        "https://www.chefkoch.de/rezepte/1000001/Kartoffel-Hack-Pfanne.html" to
            page("Kartoffel-Hack-Pfanne", "500 g Kartoffeln", "300 g Hackfleisch", "1 Zwiebel", "Salz", "1 EL Öl"),
        "https://www.chefkoch.de/rezepte/1000002/Hack-Auflauf.html" to
            page("Hack-Auflauf", "500 g Kartoffeln", "300 g Hackfleisch", "200 ml Sahne"),
        "https://www.chefkoch.de/rezepte/1000003/Gulasch.html" to
            page("Gulasch", "1 kg Rindfleisch", "3 Zwiebeln", "2 Paprika", "1 EL Tomatenmark"),
    )

    private val searchHtml = """
        <a href="/rezepte/1000001/Kartoffel-Hack-Pfanne.html">A</a>
        <a href="https://www.chefkoch.de/rezepte/1000001/Kartoffel-Hack-Pfanne.html">A doppelt</a>
        <script>{"url":"https:\/\/www.chefkoch.de\/rezepte\/1000002\/Hack-Auflauf.html"}</script>
        <a href="/rezepte/1000003/Gulasch.html">C</a>
        <a href="/rezepte/was-koche-ich-heute.html">kein Rezept</a>
        <a href="/rs/s0/hack/Rezepte.html">Suche</a>
    """.trimIndent()

    private val kitchen = KitchenState(pantry = listOf("Kartoffeln", "Hackfleisch", "Zwiebeln").map { PantryItem(it) })

    private fun finder(direct: suspend (String) -> String = { url -> load(url) }, browser: suspend (String) -> String = { url -> load(url) }) =
        SurpriseFinder(direct, browser)

    private fun load(url: String) = if ("/rs/" in url) searchHtml else pages[url] ?: throw IOException("404")

    @Test fun searchUrlCombinesTermsAndEncodesUmlauts() {
        assertEquals("https://www.chefkoch.de/rs/s0/kartoffeln+hackfleisch/Rezepte.html", ChefkochSearch.searchUrl(listOf("Kartoffeln", "Hackfleisch")))
        assertEquals("https://www.chefkoch.de/rs/s0/m%C3%B6hren/Rezepte.html", ChefkochSearch.searchUrl(listOf("Möhren")))
    }

    @Test fun recipeLinksFromSearchPage() {
        assertEquals(
            listOf(
                "https://www.chefkoch.de/rezepte/1000001/Kartoffel-Hack-Pfanne.html",
                "https://www.chefkoch.de/rezepte/1000002/Hack-Auflauf.html",
                "https://www.chefkoch.de/rezepte/1000003/Gulasch.html",
            ),
            ChefkochSearch.recipeLinks(searchHtml),
        )
    }

    @Test fun strictnessDecidesWhatCounts() = runTest {
        val strict = finder().find(kitchen, maxMissing = 0, seen = mutableSetOf(), random = Random(1))
        assertEquals(listOf("Kartoffel-Hack-Pfanne"), strict.hits.map { it.web.recipe.title })

        val relaxed = finder().find(kitchen, maxMissing = 1, seen = mutableSetOf(), random = Random(1))
        // sortiert: erst „alles da“, dann „1 fehlt“ (Sahne); Gulasch fehlt zu viel
        assertEquals(listOf("Kartoffel-Hack-Pfanne", "Hack-Auflauf"), relaxed.hits.map { it.web.recipe.title })
        assertEquals(listOf("Sahne"), relaxed.hits[1].missing.map { it.name })
    }

    @Test fun blockedDirectFetchSwitchesToBrowserOnce() = runTest {
        var directCalls = 0
        var browserCalls = 0
        val f = finder(
            direct = { directCalls++; throw IOException("Server antwortet mit HTTP 403") },
            browser = { url -> browserCalls++; load(url) },
        )
        val result = f.find(kitchen, maxMissing = 0, seen = mutableSetOf(), random = Random(2))
        assertEquals(1, result.hits.size)
        assertEquals(1, directCalls)
        assertTrue(browserCalls >= 4) // Suche + 3 Rezepte
    }

    @Test fun alreadyShownRecipesAreSkipped() = runTest {
        val seen = mutableSetOf<String>()
        finder().find(kitchen, maxMissing = 3, seen = seen, random = Random(3))
        assertEquals(3, seen.size)
        val again = finder().find(kitchen, maxMissing = 3, seen = seen, random = Random(3))
        assertTrue(again.hits.isEmpty())
    }

    @Test(expected = EmptyPantryException::class)
    fun onlyStaplesInPantryIsEmpty() = runTest {
        finder().find(KitchenState(pantry = listOf(PantryItem("Salz"), PantryItem("Öl"))), maxMissing = 1, seen = mutableSetOf())
    }

    @Test(expected = IOException::class)
    fun searchFailureIsReported() = runTest {
        finder(direct = { throw IOException("offline") }, browser = { throw IOException("offline") })
            .find(kitchen, maxMissing = 1, seen = mutableSetOf())
    }

    @Test fun anyModeIgnoresPantry() = runTest {
        val searched = mutableListOf<String>()
        val f = finder(direct = { url -> if ("/rs/" in url) searched += url; load(url) })
        val result = f.find(KitchenState(), maxMissing = 0, seen = mutableSetOf(), random = Random(4), usePantry = false, minResults = 3)
        // leerer Vorrat stört nicht, nichts wird aussortiert
        assertEquals(3, result.hits.size)
        assertTrue(searched.isNotEmpty() && searched.all { url -> SurpriseFinder.ANY_TERMS.any { url.contains(java.net.URLEncoder.encode(it.lowercase(), "UTF-8")) } })
    }

    @Test fun minResultsLimitsWork() = runTest {
        // Mit Vorrat: 3 Seiten je gewünschtem Fund, mindestens 12, höchstens 30
        assertEquals(12, SurpriseFinder.maxChecksFor(3, usePantry = true))
        assertEquals(24, SurpriseFinder.maxChecksFor(8, usePantry = true))
        assertEquals(30, SurpriseFinder.maxChecksFor(10, usePantry = true))
        assertEquals(8, SurpriseFinder.maxChecksFor(8, usePantry = false))
        // Beliebig, nur 2 gewünscht → nur 2 Seiten laden
        var recipeLoads = 0
        val f = finder(direct = { url -> if ("/rezepte/" in url) recipeLoads++; load(url) })
        val result = f.find(KitchenState(), maxMissing = 0, seen = mutableSetOf(), random = Random(5), usePantry = false, minResults = 2)
        assertEquals(2, result.hits.size)
        assertEquals(2, recipeLoads)
    }
}
