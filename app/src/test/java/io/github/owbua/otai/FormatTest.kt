package io.github.owbua.otai

import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.data.model.parseAmount
import io.github.owbua.otai.data.normalized
import io.github.owbua.otai.io.RecipeImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test fun `Beispiel Bauerntopf wird vollstaendig gelesen`() {
        val r = TestData.bauerntopf
        assertEquals("bauerntopf", r.id)
        assertEquals(3, r.servings)
        assertEquals(11, r.ingredients.size)
        assertEquals(4, r.steps.size)
        assertEquals(2400, r.steps[2].timer?.seconds)
        assertEquals(listOf("kartoffeln", "moehren", "paprika"), r.steps[0].ingredients)
        assertEquals(571.0, r.nutrition?.kcal)
        assertEquals(7, r.tips.size)
        assertNull(r.ingredients.first { it.key == "schmand" }.amount)
        // alle Schritt-Verweise zeigen auf existierende Zutaten
        val keys = r.ingredients.map { it.key }.toSet()
        r.steps.flatMap { it.ingredients }.forEach { assertTrue("unbekannter Key $it", it in keys) }
    }

    @Test fun `Beispiel Hack-Pilz-Pfanne wird vollstaendig gelesen`() {
        val r = TestData.hackPilz
        assertEquals(2, r.servings)
        assertEquals(12, r.ingredients.size)
        assertEquals(0.5, r.ingredients.first { it.key == "schnittlauch" }.amount!!, 0.0)
        assertEquals(1200, r.steps[0].timer?.seconds)
        val keys = r.ingredients.map { it.key }.toSet()
        r.steps.flatMap { it.ingredients }.forEach { assertTrue(it in keys) }
    }

    @Test fun `Beispiele bestehen die Import-Validierung`() {
        listOf("bauerntopf.json", "hack-pilz-pfanne.json").forEach {
            val batch = RecipeImporter.read(it, TestData.exampleText(it).encodeToByteArray())
            assertEquals(1, batch.recipes.size)
            assertTrue(batch.problems.isEmpty())
        }
    }

    @Test fun `Mengen als String werden verstanden`() {
        assertEquals(0.5, parseAmount("1/2")!!, 1e-9)
        assertEquals(0.5, parseAmount("½")!!, 1e-9)
        assertEquals(1.5, parseAmount("1 ½")!!, 1e-9)
        assertEquals(1.5, parseAmount("1½")!!, 1e-9)
        assertEquals(0.5, parseAmount("0,5")!!, 1e-9)
        assertEquals(2.0, parseAmount("2-3")!!, 1e-9)
        assertEquals(150.0, parseAmount("ca. 150")!!, 1e-9)
        assertNull(parseAmount("etwas"))
        assertNull(parseAmount(""))
    }

    @Test fun `tolerante Eingabe mit Codezaun Strings und fehlenden Keys`() {
        val text = """
            ```json
            {
              "title": "Miso-Suppe",
              "servings": 2,
              // Kommentare sind erlaubt
              "ingredients": ["Wakame", {"name": "Miso", "amount": "2", "unit": "EL"}, {"name": "Miso", "amount": "1/2", "unit": "TL"}],
              "steps": ["Dashi erhitzen", {"text": "Miso einrühren", "timer": {"seconds": 0}}],
              "unbekannt": true,
            }
            ```
        """.trimIndent()
        val r = RecipeJson.decodeDocument(text).single().normalized()
        assertEquals("Miso-Suppe", r.title)
        assertTrue(r.id.startsWith("miso-suppe-"))
        assertEquals(listOf("wakame", "miso", "miso-2"), r.ingredients.map { it.key })
        assertEquals(2.0, r.ingredients[1].amount!!, 0.0)
        assertEquals(0.5, r.ingredients[2].amount!!, 0.0)
        assertNull(r.ingredients[0].amount)
        assertEquals(2, r.steps.size)
        assertNull("Timer 0 s wird verworfen", r.steps[1].timer)
    }

    @Test fun `kaputtes JSON liefert verstaendlichen Fehler`() {
        val batch = RecipeImporter.read("kaputt.json", "{ \"title\": ".encodeToByteArray())
        assertTrue(batch.recipes.isEmpty())
        assertEquals("kaputt.json", batch.problems.single().sourceName)
        assertTrue(batch.problems.single().message.startsWith("Kein gültiges Rezept-JSON"))
    }

    @Test fun `Rezept ohne Titel oder Inhalt wird abgelehnt`() {
        val batch = RecipeImporter.read("x.json", """[{"title":"","steps":["a"]},{"title":"Leer"},{"format":"kochbuch-rezept/2","title":"Neu","steps":["a"]}]""".encodeToByteArray())
        assertTrue(batch.recipes.isEmpty())
        assertEquals(
            listOf("Titel fehlt", "Weder Zutaten noch Zubereitungsschritte vorhanden", "Formatversion kochbuch-rezept/2 ist neuer als diese App"),
            batch.problems.map { it.message },
        )
    }

    @Test fun `Export und erneutes Lesen ergeben dasselbe Rezept`() {
        val r = TestData.bauerntopf
        assertEquals(r, RecipeJson.decode(RecipeJson.encode(r)).normalized())
    }
}
