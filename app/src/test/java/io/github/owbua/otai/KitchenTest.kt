package io.github.owbua.otai

import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.PantryItem
import io.github.owbua.otai.data.model.PlanEntry
import io.github.owbua.otai.data.model.ShoppingItem
import io.github.owbua.otai.domain.MealSuggester
import io.github.owbua.otai.domain.ShoppingPlanner
import io.github.owbua.otai.domain.ingredientStem
import io.github.owbua.otai.io.RecipeExporter
import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.web.WebFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2026, 11, 16) // Montag
    private val recipes get() = listOf(TestData.bauerntopf, TestData.hackPilz)

    private fun entry(day: Long, id: String, servings: Int, cooked: Boolean = false) =
        PlanEntry("e$day$id", today.plusDays(day).toString(), id, servings, cooked)

    @Test fun `Wortstaemme vergleichen Singular und Plural`() {
        assertEquals(ingredientStem("Kartoffel(n)"), ingredientStem("Kartoffeln, vorwiegend festkochende"))
        assertEquals(ingredientStem("Möhre(n)"), ingredientStem("Möhren"))
        assertEquals(ingredientStem("Tomate"), ingredientStem("Tomaten, stückige"))
    }

    @Test fun `Einkaufsliste fasst Plan zusammen und laesst Grundzutaten und Vorrat weg`() {
        val state = KitchenState(
            plan = listOf(
                entry(0, "bauerntopf", 6),           // doppelte Menge
                entry(1, "hack-pilz-pfanne", 2),
                entry(-1, "hack-pilz-pfanne", 2),    // gestern → zählt nicht
                entry(2, "bauerntopf", 3, cooked = true), // gekocht → zählt nicht
            ),
            pantry = listOf(PantryItem("Tomatenmark")),
            manualItems = listOf(ShoppingItem("m1", "Milch", 1.0, "l")),
            checkedPlanKeys = setOf("plan:hackfleisch|g"),
        )
        val lines = ShoppingPlanner.build(state, recipes, today)
        val byName = lines.associateBy { it.name }
        // Kartoffeln: 1000 g (Bauerntopf ×2) + 400 g → 1,4 kg
        val kart = lines.single { ingredientStem(it.name) == "kartoffel" }
        assertEquals(1.4, kart.amount!!, 1e-9)
        assertEquals("kg", kart.unit)
        assertEquals(listOf("Bauerntopf", "Hack-Pilz-Pfanne"), kart.recipes)
        // Hackfleisch: 1000 g + 250 g = 1,25 kg, abgehakt
        val hack = lines.single { it.key == "plan:hackfleisch|g" }
        assertTrue(hack.checked)
        assertEquals(1.25, hack.amount!!, 1e-9)
        assertNull("Grundzutat", byName["Öl"])
        assertNull("Grundzutat", byName["Salz"])
        assertNull("im Vorrat", byName["Tomatenmark"])
        assertNotNull(byName["Schmand"]) // n. B., aber kein Vorrat → steht drauf
        assertEquals(1.0, byName.getValue("Milch").amount!!, 0.0)
        assertTrue("abgehakte ans Ende", lines.last().checked)
        val text = ShoppingPlanner.asText(lines, today)
        assertTrue(text.contains("• 1,4 kg Kartoffel(n)"))
        assertFalse(text.contains("Hackfleisch"))
    }

    @Test fun `Vorschlag nutzt gemeinsame Zutaten und meidet Wiederholung`() {
        val extra = io.github.owbua.otai.data.model.Recipe(id = "salat", title = "Gurkensalat", servings = 2,
            ingredients = listOf(io.github.owbua.otai.data.model.Ingredient(key = "gurke", amount = 1.0, name = "Gurke")), steps = listOf(io.github.owbua.otai.data.model.Step("Schneiden")))
        val all = recipes + extra
        val state = KitchenState(plan = listOf(entry(0, "bauerntopf", 3)))
        val s = MealSuggester.suggest(today.plusDays(2), all, state, today)!!
        // Bauerntopf ist schon geplant (Wiederholung), Hack-Pilz-Pfanne teilt Kartoffeln/Hackfleisch
        assertEquals("hack-pilz-pfanne", s.recipe.id)
        assertTrue(s.reason, s.reason.startsWith("Nutzt ebenfalls"))
        assertTrue(s.reason, s.reason.contains("Bauerntopf am Mo"))
        val other = MealSuggester.suggest(today.plusDays(2), all, state, today, exclude = setOf("hack-pilz-pfanne"))!!
        assertEquals("salat", other.recipe.id)
    }

    @Test fun `Kochen markiert Plan und leert Vorrat, Backup wird zusammengefuehrt`() = runTest(UnconfinedTestDispatcher()) {
        val repo = KitchenRepository(tmp.newFolder(), backgroundScope, Dispatchers.Unconfined)
        repo.addToPlan(today, "hack-pilz-pfanne", 2)
        repo.addPantry("Champignons", today)
        repo.addPantry("Reis", today)
        repo.markCooked(TestData.hackPilz, today.plusDays(1))
        val s = repo.current()
        assertTrue(s.plan.single().cooked)
        assertEquals(listOf("Reis"), s.pantry.map { it.name })

        // Einkauf: abgehakt → Vorrat
        repo.addToPlan(today.plusDays(1), "bauerntopf", 3)
        val lines = ShoppingPlanner.build(repo.current(), recipes, today)
        lines.filter { it.name.startsWith("Kartoffel") }.forEach { repo.toggle(it) }
        repo.moveCheckedToPantry(ShoppingPlanner.build(repo.current(), recipes, today), today)
        assertTrue(repo.current().pantry.any { it.name.startsWith("Kartoffel") })
        assertTrue(ShoppingPlanner.build(repo.current(), recipes, today).none { it.name.startsWith("Kartoffel") })

        // ZIP-Backup enthält den Küchenzustand
        val zip = ByteArrayOutputStream().also { RecipeExporter.writeZip(it, recipes, { null }, repo.current()) }.toByteArray()
        val batch = RecipeImporter.read("b.zip", zip)
        assertEquals(2, batch.recipes.size)
        val k = batch.kitchen!!
        val targetDir = tmp.newFolder()
        val target = KitchenRepository(targetDir, backgroundScope, Dispatchers.Unconfined)
        target.addPantry("Reis", today)
        target.merge(k, setOf("bauerntopf", "hack-pilz-pfanne"))
        target.merge(k, setOf("bauerntopf", "hack-pilz-pfanne")) // zweimal → keine Duplikate
        val t = target.current()
        assertEquals(2, t.plan.size)
        assertEquals(1, t.pantry.count { it.name == "Reis" })
        // Persistenz
        val reloaded = KitchenRepository(targetDir, backgroundScope, Dispatchers.Unconfined).current()
        assertEquals(t.plan.size, reloaded.plan.size)
    }

    @Test fun `Bildformate werden an der Signatur erkannt`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals("jpeg", WebFetcher.sniffImage(jpeg))
        assertEquals("avif", WebFetcher.sniffImage(byteArrayOf(0, 0, 0, 0x1C) + "ftypavif".encodeToByteArray() + ByteArray(8)))
        assertEquals("webp", WebFetcher.sniffImage("RIFF".encodeToByteArray() + ByteArray(4) + "WEBPVP8 ".encodeToByteArray()))
        assertNull(WebFetcher.sniffImage("<!DOCTYPE html><html>".encodeToByteArray()))
        val zipped = TestData.zipWithPhotos.values.first().second.readBytes()
        assertEquals("jpeg", WebFetcher.sniffImage(zipped))
    }

    // --- Filter „Aus Vorrat“ ----------------------------------------------------------------------

    private fun pantryOf(vararg names: String) = KitchenState(pantry = names.map { PantryItem(it) })
    private val bauerntopfVorrat = arrayOf("Kartoffeln", "Möhren", "Paprikaschoten", "Hackfleisch", "Tomaten", "Tomatenmark", "Gemüsebrühe")

    @Test fun pantryFilterShowsOnlyRecipesWithEverythingAtHome() {
        val all = listOf(TestData.bauerntopf, TestData.hackPilz)
        val state = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, pantryOf(*bauerntopfVorrat))
        assertTrue(state.chips.any { it.label == "Aus Vorrat" && it.selected })
        // Öl, Zucker, Salz sind Grundzutaten, Schmand ist „nach Belieben“
        assertEquals(listOf("Bauerntopf"), state.recipes.map { it.title })
        assertNull(state.emptyHint)
    }

    @Test fun pantryFilterNeedsEveryRequiredIngredient() {
        val ohneHack = bauerntopfVorrat.filter { it != "Hackfleisch" }.toTypedArray()
        assertFalse(io.github.owbua.otai.domain.cookableFromPantry(TestData.bauerntopf, pantryOf(*ohneHack)))
        assertTrue(io.github.owbua.otai.domain.cookableFromPantry(TestData.bauerntopf, pantryOf(*bauerntopfVorrat)))
        assertFalse(io.github.owbua.otai.domain.cookableFromPantry(TestData.hackPilz, pantryOf(*bauerntopfVorrat)))
    }

    @Test fun pantryFilterExplainsEmptyResult() {
        val all = listOf(TestData.bauerntopf, TestData.hackPilz)
        val empty = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, KitchenState())
        assertTrue(empty.recipes.isEmpty())
        assertEquals("Vorrat ist leer", empty.emptyHint?.first)
        val some = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, pantryOf("Kartoffeln"))
        assertEquals("Noch nicht alles da", some.emptyHint?.first)
    }

    @Test fun pantryFilterToleratesMissingIngredientsWhenAllowed() {
        val all = listOf(TestData.bauerntopf, TestData.hackPilz)
        val ohneHack = bauerntopfVorrat.filter { it != "Hackfleisch" }.toTypedArray()
        val strict = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, pantryOf(*ohneHack))
        assertTrue(strict.recipes.isEmpty())
        val relaxed = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, pantryOf(*ohneHack), pantryMaxMissing = 1)
        assertEquals(listOf("Bauerntopf"), relaxed.recipes.map { it.title })
        assertEquals("Hackfleisch", relaxed.cards.single().missing)
        // mit vollem Vorrat steht „alles da“ vorn und zeigt nichts Fehlendes
        val full = io.github.owbua.otai.ui.overview.buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, pantryOf(*bauerntopfVorrat), pantryMaxMissing = 3)
        assertEquals("Bauerntopf", full.recipes.first().title)
        assertNull(full.cards.first().missing)
    }
}
