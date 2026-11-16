package io.github.owbua.otai

import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.web.IngredientParser
import io.github.owbua.otai.io.web.JsonLdRecipeParser
import io.github.owbua.otai.io.web.NoRecipeFoundException
import io.github.owbua.otai.io.web.RecipeEnricher
import io.github.owbua.otai.io.web.WebFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebImportTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("web/$name")!!.readText()

    @Test fun `Zutatenzeilen werden zerlegt`() {
        fun p(s: String) = IngredientParser.parse(s).let { listOf(it.amount, it.unit, it.name, it.note) }
        assertEquals(listOf(500.0, "g", "Kartoffel(n)", null), p("500 g Kartoffel(n)"))
        assertEquals(listOf(2.0, null, "Möhre(n)", "ca. 150 g"), p("2  Möhre(n) , ca. 150 g"))
        assertEquals(listOf(2.0, null, "Paprikaschote(n), bunte", "ca. 250 g"), p("2  Paprikaschote(n), bunte, ca. 250 g"))
        assertEquals(listOf(400.0, "g", "Tomaten, stückige", "aus der Dose"), p("400 g Tomaten, stückige, aus der Dose"))
        assertEquals(listOf(1.0, "TL", "Zucker", "gehäuft"), p("1 TL, gehäuft Zucker"))
        assertEquals(listOf(null, null, "Salz", null), p(" Salz"))
        assertEquals(listOf(null, null, "Schmand", "nach Belieben"), p("n. B. Schmand"))
        assertEquals(listOf(0.5, "TL", "Salz", null), p("1/2 TL Salz"))
        assertEquals(listOf(0.5, "Bund", "Schnittlauch", null), p("½ Bund Schnittlauch"))
        assertEquals(listOf(2.0, null, "Frühlingszwiebeln", "2–3"), p("2-3 Frühlingszwiebeln"))
        assertEquals(listOf(1.5, "EL", "Sojasauce", null), p("1,5 Esslöffel Sojasauce"))
        assertEquals(listOf(3.0, "Zehe(n)", "Knoblauch", null), p("3 Zehe(n) Knoblauch"))
        assertEquals(listOf(1.0, "Pck.", "Backpulver", null), p("1 Pck. Backpulver"))
        assertEquals(listOf(2.0, null, "Eier", null), p("2 Eier"))
        assertEquals(listOf(200.0, "g", "Gouda", null), p("200g Gouda"))
    }

    @Test fun `Chefkoch-Seite wird komplett uebernommen`() {
        val url = "https://www.chefkoch.de/rezepte/1000000000000001/Bauerntopf.html"
        val web = JsonLdRecipeParser.parse(res("chefkoch-bauerntopf.html"), url)
        val r = web.recipe
        assertEquals("Bauerntopf", r.title)
        assertEquals("bauerntopf", r.id)
        assertEquals(3, r.servings)
        assertEquals(60, r.times.totalMin)
        assertEquals(20, r.times.activeMin)
        assertEquals(40, r.times.cookMin)
        assertEquals("Hauptspeise", r.category)
        assertTrue(r.description.contains("Hackfleisch & Gemüse"))
        assertEquals(11, r.ingredients.size)
        assertEquals(4, r.steps.size)
        assertEquals(571.0, r.nutrition!!.kcal!!, 0.0)
        assertEquals(31.9, r.nutrition!!.proteinG!!, 1e-9)
        assertEquals("Testkoch", r.source?.author)
        assertEquals("Chefkoch.de", r.source?.name)
        assertEquals(url, r.source?.url)
        assertEquals("https://img.chefkoch-cdn.de/rezepte/1000000000000001/bilder/1000001/crop-960x540/bauerntopf.jpg", web.imageUrl)
        // automatisch abgeleitet
        assertEquals(2400, r.steps[2].timer?.seconds)
        assertEquals("Köcheln", r.steps[2].timer?.label)
        assertNull(r.steps[0].timer)
        val step1 = r.steps[0].ingredients
        assertTrue(step1.containsAll(listOf("kartoffel", "moehre", "paprikaschote")))
        assertTrue(r.steps[1].ingredients.containsAll(listOf("oel", "hackfleisch")))
        assertTrue("schmand" in r.steps[3].ingredients)
        assertNull(RecipeImporter.validate(r))
    }

    @Test fun `WordPress-Graph mit Abschnitten relativen Bildern und Entities`() {
        val web = JsonLdRecipeParser.parse(res("wordpress-graph.html"), "https://blog.example/okonomiyaki/")
        val r = web.recipe
        assertEquals("Okonomiyaki – japanischer Pfannkuchen", r.title)
        assertEquals("https://blog.example/wp-content/uploads/okonomiyaki.webp", web.imageUrl)
        assertEquals(2, r.servings)
        assertEquals(35, r.times.totalMin)
        assertEquals(listOf("Japanisch"), r.tags)
        assertEquals(4, r.steps.size)
        assertEquals(900, r.steps[0].timer?.seconds)
        assertEquals("Ruhen lassen", r.steps[0].timer?.label)
        assertEquals(300, r.steps[2].timer?.seconds) // „4–5 Min.“ → obere Grenze
        assertEquals("Aiko", r.source?.author)
        assertEquals("blog.example", r.source?.name)
        assertNull(r.ingredients.last().amount)
    }

    @Test(expected = NoRecipeFoundException::class)
    fun `Seite ohne Rezeptdaten`() {
        JsonLdRecipeParser.parse("<html><script type=\"application/ld+json\">{\"@type\":\"WebPage\"}</script></html>", "https://x.de")
    }

    @Test fun `Timer-Erkennung`() {
        assertEquals(5400, RecipeEnricher.timerFor("Im Ofen 1,5 Stunden backen.")?.seconds)
        assertEquals("Backen", RecipeEnricher.timerFor("Im Ofen 1,5 Stunden backen.")?.label)
        assertEquals(null, RecipeEnricher.timerFor("Alles gut verrühren."))
        assertEquals(null, RecipeEnricher.timerFor("Auf 180 °C vorheizen.")) // keine Zeitangabe
    }

    @Test fun `Links aus geteiltem Text`() {
        assertEquals("https://www.chefkoch.de/rezepte/1/Bauerntopf.html", WebFetcher.findUrl("Schau mal: https://www.chefkoch.de/rezepte/1/Bauerntopf.html."))
        assertNull(WebFetcher.findUrl("kein Link"))
    }
}
