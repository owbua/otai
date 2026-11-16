package io.github.owbua.otai

import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.RecipeStore
import io.github.owbua.otai.domain.KiPrompt
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.io.RecipeExporter
import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.RecipePackage
import io.github.owbua.otai.ui.edit.EditViewModel
import io.github.owbua.otai.ui.edit.newRecipeDraft
import io.github.owbua.otai.ui.overview.OverviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun promptFile() = listOf(File("../rezept-format/claude-prompt.md"), File("rezept-format/claude-prompt.md")).first { it.isFile }

    @Test fun `KI-Prompt wird korrekt aus claude-prompt md geschnitten`() {
        val p = KiPrompt.parse(promptFile().readText())
        assertTrue(p.main, p.main.startsWith("Wandle das angehängte Rezept"))
        assertTrue(p.main.contains("kochbuch-rezept/1"))
        assertTrue("Regel 13 gehört dazu", p.main.contains("13. Felder wie `favorite`"))
        assertFalse("Hinweise für Menschen gehören nicht in den Prompt", p.main.contains("Kopiere alles unterhalb"))
        assertFalse(p.main.contains("### Zusatz"))
        assertTrue(p.packageAddon, p.packageAddon.startsWith("Erstelle statt des Codeblocks eine ZIP-Datei"))
        assertTrue(p.withPackage.startsWith(p.main))
        assertTrue(p.withPackage.contains("Variante: Rezept-Paket"))
    }

    @Test fun `Neues Rezept anlegen - Pflichtfeld, Speichern, Schritt-Zutaten`() = runTest(dispatcher) {
        val repo = RecipeRepository(RecipeStore(tmp.newFolder()), backgroundScope, Dispatchers.Unconfined)
        val vm = EditViewModel(repo, null)
        assertTrue(vm.state.value.isNew)
        assertEquals(newRecipeDraft().ingredients.size, vm.state.value.ingredients.size)

        vm.save()
        assertEquals("Bitte einen Titel eingeben", vm.state.value.error)
        vm.update { it.copy(title = "Rührei") }
        vm.save()
        assertEquals("Weder Zutaten noch Zubereitungsschritte vorhanden", vm.state.value.error)

        val ing = vm.state.value.ingredients.single().uid
        vm.updateIngredient(ing) { it.copy(amount = "3", name = "Eier") }
        vm.addIngredient()
        vm.updateIngredient(vm.state.value.ingredients.last().uid) { it.copy(amount = "1/2", unit = "TL", name = "Salz") }
        vm.updateStep(vm.state.value.steps.single().uid) { it.copy(text = "Eier mit Salz verquirlen und stocken lassen.", timerMin = "4") }
        assertTrue(vm.isDirty())
        vm.save()
        assertNull(vm.state.value.error)
        val id = vm.state.value.savedId!!
        assertTrue(id.startsWith("ruehrei-"))
        val r = repo.get(id)!!
        assertEquals("Rührei", r.title)
        assertEquals(listOf(3.0, 0.5), r.ingredients.map { it.amount })
        assertEquals(240, r.steps.single().timer?.seconds)
        assertEquals("Zutaten-Bezüge automatisch", setOf("eier", "salz"), r.steps.single().ingredients.toSet())
        assertNotNull(r.createdAt)
    }

    @Test fun `Bearbeiten ohne Aenderung ist nicht dirty`() = runTest(dispatcher) {
        val repo = RecipeRepository(RecipeStore(tmp.newFolder()), backgroundScope, Dispatchers.Unconfined)
        repo.import(listOf(ImportedRecipe(TestData.bauerntopf, null, "x")), ConflictMode.COPY)
        val vm = EditViewModel(repo, "bauerntopf")
        assertFalse(vm.state.value.isNew)
        assertFalse(vm.isDirty())
        vm.update { it.copy(title = "Bauerntopf deluxe") }
        assertTrue(vm.isDirty())
        vm.save()
        assertEquals("bauerntopf", vm.state.value.savedId)
        assertEquals("Bauerntopf deluxe", repo.get("bauerntopf")!!.title)
        // Schritt-Zutaten bleiben erhalten
        assertEquals(TestData.bauerntopf.steps.map { it.ingredients }, repo.get("bauerntopf")!!.steps.map { it.ingredients })
    }

    @Test fun `Mehrfachauswahl - Favorit und Loeschen inkl Wochenplan`() = runTest(dispatcher) {
        val dir = tmp.newFolder()
        val repo = RecipeRepository(RecipeStore(dir), backgroundScope, Dispatchers.Unconfined)
        val kitchen = KitchenRepository(dir, backgroundScope, Dispatchers.Unconfined)
        repo.import(listOf(TestData.bauerntopf, TestData.hackPilz).map { ImportedRecipe(it, null, it.id) }, ConflictMode.COPY)
        kitchen.addToPlan(java.time.LocalDate.now(), "bauerntopf", 3)
        val vm = OverviewViewModel(repo, kitchen)
        val collect = backgroundScope.launch { vm.state.collect {} }

        vm.toggleSelection("bauerntopf")
        vm.toggleSelection("hack-pilz-pfanne")
        assertEquals(setOf("bauerntopf", "hack-pilz-pfanne"), vm.state.value.selected)
        vm.toggleFavoriteSelected()
        assertTrue(repo.all().all { it.favorite })
        vm.toggleFavoriteSelected()
        assertTrue(repo.all().none { it.favorite })

        vm.toggleSelection("hack-pilz-pfanne") // abwählen
        vm.deleteSelected()
        assertEquals(listOf("hack-pilz-pfanne"), repo.all().map { it.id })
        assertTrue(kitchen.current().plan.isEmpty())
        assertTrue(vm.state.value.selected.isEmpty())
        vm.selectAllVisible()
        assertEquals(setOf("hack-pilz-pfanne"), vm.state.value.selected)
        collect.cancel()
    }

    @Test fun `Auswahl als Rezept-Paket exportieren und wieder importieren`() = runTest(dispatcher) {
        val repo = RecipeRepository(RecipeStore(tmp.newFolder()), backgroundScope, Dispatchers.Unconfined)
        repo.import(listOf(TestData.bauerntopf, TestData.hackPilz).map { ImportedRecipe(it, null, it.id) }, ConflictMode.COPY)
        val jpeg = TestData.zipWithPhotos.getValue("bauerntopf").second.readBytes()
        repo.setPhoto("bauerntopf", jpeg)

        val out = ByteArrayOutputStream()
        RecipeExporter.writePackage(out, repo.all(), { repo.photoFile(it) })
        val batch = RecipeImporter.read("auswahl.zip", out.toByteArray())
        assertTrue(batch.isPackage)
        assertEquals(setOf("bauerntopf", "hack-pilz-pfanne"), batch.recipes.map { it.recipe.id }.toSet())
        assertTrue(batch.recipes.all { it.recipe.photo == null })
        assertEquals("bauerntopf.jpg", batch.packagePhotos.single().fileName)
        assertArrayEquals(jpeg, batch.packagePhotos.single().bytes)
        val plan = RecipePackage.planPhotos(batch.packagePhotos, batch.packageRecipeIds, mapOf("bauerntopf" to "bauerntopf"))
        assertEquals("bauerntopf", plan.assignments.single().targetId)
        assertEquals("bauerntopf.zip", RecipeExporter.packageFileName(listOf(TestData.bauerntopf)))
        assertEquals("otai-2-rezepte.zip", RecipeExporter.packageFileName(repo.all()))
    }
}
