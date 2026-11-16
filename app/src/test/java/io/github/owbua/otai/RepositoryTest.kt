package io.github.owbua.otai

import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.RecipeStore
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.io.RecipeExporter
import io.github.owbua.otai.io.RecipeImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(dir: java.io.File, scope: kotlinx.coroutines.CoroutineScope) =
        RecipeRepository(RecipeStore(dir), scope, Dispatchers.Unconfined)

    @Test fun `Import Konflikte und Persistenz`() = runTest(UnconfinedTestDispatcher()) {
        val dir = tmp.newFolder()
        val repo = repo(dir, backgroundScope)
        val items = listOf(TestData.bauerntopf, TestData.hackPilz).map { ImportedRecipe(it, null, it.id) }
        assertEquals(2, repo.import(items, ConflictMode.COPY).added)

        assertEquals(2, repo.conflicts(items).size)
        assertEquals(2, repo.import(items, ConflictMode.SKIP).skipped)
        assertEquals(2, repo.import(items, ConflictMode.REPLACE).replaced)
        assertEquals(2, repo.all().size)
        repo.import(items.take(1), ConflictMode.COPY)
        val all = repo.all()
        assertEquals(3, all.size)
        assertEquals(2, all.count { it.title == "Bauerntopf" })
        assertNotEquals(all.filter { it.title == "Bauerntopf" }[0].id, all.filter { it.title == "Bauerntopf" }[1].id)

        repo.update("bauerntopf") { it.copy(favorite = true) }
        // neu laden von der Platte
        val reloaded = repo(dir, backgroundScope).all()
        assertEquals(3, reloaded.size)
        assertTrue(reloaded.first { it.id == "bauerntopf" }.favorite)
    }

    @Test fun `ZIP-Backup mit Foto uebersteht Export und Import`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(tmp.newFolder(), backgroundScope)
        repo.import(listOf(TestData.bauerntopf, TestData.hackPilz).map { ImportedRecipe(it, null, it.id) }, ConflictMode.COPY)
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)
        repo.setPhoto("bauerntopf", jpeg)
        repo.update("hack-pilz-pfanne") { it.copy(favorite = true, cookedCount = 3) }

        val zip = ByteArrayOutputStream().also { out -> RecipeExporter.writeZip(out, repo.all(), { repo.photoFile(it) }) }.toByteArray()
        val batch = RecipeImporter.read("backup.zip", zip)
        assertTrue(batch.problems.isEmpty())
        assertEquals(2, batch.recipes.size)
        assertArrayEquals(jpeg, batch.recipes.first { it.recipe.id == "bauerntopf" }.photo)

        val target = repo(tmp.newFolder(), backgroundScope)
        target.import(batch.recipes, ConflictMode.COPY)
        val restored = target.all()
        val original = repo.all()
        original.forEach { o ->
            val r = restored.first { it.id == o.id }
            assertEquals(o.copy(photo = null, updatedAt = null, createdAt = null), r.copy(photo = null, updatedAt = null, createdAt = null))
        }
        assertArrayEquals(jpeg, target.photoFile(restored.first { it.id == "bauerntopf" }.photo)!!.readBytes())
    }

    @Test fun `Einzel-Export enthaelt keinen Fotoverweis`() = runTest(UnconfinedTestDispatcher()) {
        val out = ByteArrayOutputStream()
        RecipeExporter.writeJson(out, TestData.bauerntopf.copy(photo = "x.jpg"))
        val back = RecipeImporter.read("a.json", out.toByteArray()).recipes.single().recipe
        assertEquals(null, back.photo)
        assertEquals("bauerntopf", back.id)
    }

    @Test fun `Beispiel-ZIP enthaelt beide Rezepte mit Foto`() {
        val all = TestData.zipWithPhotos
        assertEquals(setOf("bauerntopf", "hack-pilz-pfanne"), all.keys)
        all.values.forEach { (recipe, photo) ->
            assertTrue(photo.length() > 10_000)
            assertEquals(TestData.example("${recipe.id}.json"), recipe.copy(photo = null))
        }
    }
}
