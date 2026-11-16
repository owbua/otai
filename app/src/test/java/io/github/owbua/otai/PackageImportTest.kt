package io.github.owbua.otai

import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.RecipeStore
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.RecipePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Baut Test-Pakete im Speicher. */
fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
    ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
}.toByteArray()

@OptIn(ExperimentalCoroutinesApi::class)
class PackageImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(20)
    private val recipesJson get() = RecipeJson.encodeList(listOf(TestData.bauerntopf, TestData.hackPilz))

    @Test fun `Paket wird erkannt - auch im Oberordner - und Fotos gesammelt`() {
        val zip = zipOf(
            "MeinPaket/rezepte.json" to recipesJson.encodeToByteArray(),
            "MeinPaket/fotos/bauerntopf.JPG" to jpg,
            "MeinPaket/fotos/hack-pilz-pfanne.webp" to jpg,
            "MeinPaket/fotos/katze.png" to jpg,
            "MeinPaket/fotos/liesmich.txt" to "hallo".encodeToByteArray(),
            "MeinPaket/fotos/alt/bauerntopf.jpg" to jpg,
            "__MACOSX/MeinPaket/fotos/._bauerntopf.JPG" to jpg,
            "MeinPaket/.DS_Store" to ByteArray(4),
        )
        val batch = RecipeImporter.read("paket.zip", zip)
        assertTrue(batch.isPackage)
        assertEquals(listOf("bauerntopf", "hack-pilz-pfanne"), batch.recipes.map { it.recipe.id })
        assertTrue(batch.recipes.all { it.photo == null && it.recipe.photo == null })
        assertEquals(setOf("bauerntopf.JPG", "hack-pilz-pfanne.webp", "katze.png"), batch.packagePhotos.map { it.fileName }.toSet())
        assertEquals(setOf("bauerntopf", "hack-pilz-pfanne", "katze"), batch.packagePhotos.map { it.recipeId }.toSet())
        assertEquals(mapOf("paket.zip" to setOf("bauerntopf", "hack-pilz-pfanne")), batch.packageRecipeIds)
        val problems = batch.problems.associate { it.sourceName to it.message }
        assertEquals("Kein unterstütztes Bildformat (jpg, jpeg, png, webp)", problems["paket.zip/fotos/liesmich.txt"])
        assertEquals("Unterordner in fotos/ werden nicht unterstützt", problems["paket.zip/fotos/alt/bauerntopf.jpg"])
        assertEquals(2, problems.size)
    }

    @Test fun `einzelnes Rezept statt Array und ohne Fotos`() {
        val zip = zipOf("rezepte.json" to RecipeJson.encode(TestData.hackPilz).encodeToByteArray())
        val batch = RecipeImporter.read("x.zip", zip)
        assertTrue(batch.isPackage)
        assertEquals(1, batch.recipes.size)
        assertTrue(batch.packagePhotos.isEmpty())
    }

    @Test fun `Backup-Format bleibt unveraendert lesbar`() {
        val backup = zipOf(
            "rezepte/bauerntopf.json" to RecipeJson.encode(TestData.bauerntopf.copy(photo = "bauerntopf-1.jpg")).encodeToByteArray(),
            "fotos/bauerntopf-1.jpg" to jpg,
        )
        val batch = RecipeImporter.read("backup.zip", backup)
        assertFalse(batch.isPackage)
        assertTrue(batch.recipes.single().photo!!.contentEquals(jpg))
    }

    @Test fun `Foto-Zuordnung - Kopie, Ueberspringen, ohne Rezept, doppelt, Gross-Kleinschreibung`() {
        val batch = RecipeImporter.read(
            "p.zip",
            zipOf(
                "rezepte.json" to recipesJson.encodeToByteArray(),
                "fotos/Bauerntopf.jpg" to jpg,          // Groß-/Kleinschreibung egal
                "fotos/bauerntopf.png" to jpg,          // zweites Foto für dasselbe Rezept
                "fotos/hack-pilz-pfanne.jpeg" to jpg,   // Rezept wurde übersprungen
                "fotos/katze.jpg" to jpg,               // kein Rezept
            ),
        )
        // bauerntopf als Kopie unter neuer ID, hack-pilz-pfanne übersprungen
        val plan = RecipePackage.planPhotos(batch.packagePhotos, batch.packageRecipeIds, mapOf("bauerntopf" to "bauerntopf-ab12cd"))
        assertEquals(1, plan.assignments.size)
        assertEquals("Bauerntopf.jpg", plan.assignments.single().photo.fileName)
        assertEquals("bauerntopf-ab12cd", plan.assignments.single().targetId)
        val reasons = plan.skipped.associate { it.sourceName.substringAfterLast('/') to it.message }
        assertEquals("Weiteres Foto für „bauerntopf“ – nur das erste wird verwendet", reasons["bauerntopf.png"])
        assertEquals("Rezept „hack-pilz-pfanne“ wurde nicht importiert", reasons["hack-pilz-pfanne.jpeg"])
        assertEquals("Kein Rezept mit der ID „katze“ im Paket", reasons["katze.jpg"])
    }

    @Test fun `Fotos nur Rezepten aus demselben Paket zuordnen`() {
        val a = RecipeImporter.read("a.zip", zipOf("rezepte.json" to RecipeJson.encode(TestData.bauerntopf).encodeToByteArray()))
        val b = RecipeImporter.read("b.zip", zipOf("rezepte.json" to RecipeJson.encode(TestData.hackPilz).encodeToByteArray(), "fotos/bauerntopf.jpg" to jpg))
        val both = a + b
        val plan = RecipePackage.planPhotos(both.packagePhotos, both.packageRecipeIds, mapOf("bauerntopf" to "bauerntopf", "hack-pilz-pfanne" to "hack-pilz-pfanne"))
        assertTrue(plan.assignments.isEmpty())
        assertEquals("Kein Rezept mit der ID „bauerntopf“ im Paket", plan.skipped.single().message)
    }

    @Test fun `Repository meldet gespeicherte IDs je Konfliktmodus`() = runTest(UnconfinedTestDispatcher()) {
        val repo = RecipeRepository(RecipeStore(tmp.newFolder()), backgroundScope, Dispatchers.Unconfined)
        val items = listOf(TestData.bauerntopf, TestData.hackPilz).map { ImportedRecipe(it, null, it.id) }
        assertEquals(mapOf("bauerntopf" to "bauerntopf", "hack-pilz-pfanne" to "hack-pilz-pfanne"), repo.import(items, ConflictMode.COPY).idMap)
        val copy = repo.import(items.take(1), ConflictMode.COPY).idMap
        assertNotEquals("bauerntopf", copy.getValue("bauerntopf"))
        assertTrue(copy.getValue("bauerntopf").startsWith("bauerntopf-"))
        assertEquals(emptyMap<String, String>(), repo.import(items, ConflictMode.SKIP).idMap)
        assertEquals("bauerntopf", repo.import(items.take(1), ConflictMode.REPLACE).idMap.getValue("bauerntopf"))
    }
}
