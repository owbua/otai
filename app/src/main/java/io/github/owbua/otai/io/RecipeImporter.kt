package io.github.owbua.otai.io

import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.normalized
import kotlinx.serialization.SerializationException
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ImportedRecipe(val recipe: Recipe, val photo: ByteArray?, val sourceName: String)

data class ImportProblem(val sourceName: String, val message: String)

class ImportBatch(
    val recipes: List<ImportedRecipe>,
    val problems: List<ImportProblem>,
    /** Wochenplan, Einkaufsliste und Vorrat aus einem Backup. */
    val kitchen: KitchenState? = null,
    /** Fotos aus Rezept-Paketen – werden erst nach dem Import über die ID zugeordnet. */
    val packagePhotos: List<PackagePhoto> = emptyList(),
    /** Paketname → IDs der Rezepte im Paket (für die Foto-Zuordnung). */
    val packageRecipeIds: Map<String, Set<String>> = emptyMap(),
) {
    val isPackage get() = packageRecipeIds.isNotEmpty()

    operator fun plus(other: ImportBatch) = ImportBatch(
        recipes + other.recipes,
        problems + other.problems,
        other.kitchen ?: kitchen,
        packagePhotos + other.packagePhotos,
        packageRecipeIds + other.packageRecipeIds,
    )

    companion object {
        val EMPTY = ImportBatch(emptyList(), emptyList())
    }
}

/**
 * Liest Rezeptdateien (.json), Rezept-Pakete (.zip mit rezepte.json + fotos/) und otai-Backups
 * (.zip mit Ordner rezepte). Rein JVM, ohne Android-Abhängigkeiten.
 */
object RecipeImporter {
    private const val MAX_ZIP_ENTRY = 20L * 1024 * 1024

    fun read(fileName: String, bytes: ByteArray): ImportBatch =
        if (isZip(bytes)) readZip(fileName, bytes) else readJson(fileName, bytes.decodeToString())

    fun readJson(sourceName: String, text: String): ImportBatch {
        val decoded = try {
            RecipeJson.decodeDocument(text)
        } catch (e: SerializationException) {
            return ImportBatch(emptyList(), listOf(ImportProblem(sourceName, "Kein gültiges Rezept-JSON: ${e.shortMessage()}")))
        } catch (e: IllegalArgumentException) {
            return ImportBatch(emptyList(), listOf(ImportProblem(sourceName, "Kein gültiges Rezept-JSON: ${e.shortMessage()}")))
        }
        val ok = mutableListOf<ImportedRecipe>()
        val problems = mutableListOf<ImportProblem>()
        decoded.forEachIndexed { i, raw ->
            val label = if (decoded.size > 1) "$sourceName (#${i + 1})" else sourceName
            val error = validate(raw)
            if (error != null) problems += ImportProblem(label, error)
            else ok += ImportedRecipe(raw.normalized(), null, label)
        }
        return ImportBatch(ok, problems)
    }

    private fun readZip(sourceName: String, bytes: ByteArray): ImportBatch {
        val entries = mutableListOf<Pair<String, ByteArray?>>() // null = zu groß
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || RecipePackage.isJunk(entry.name)) continue
                entries += entry.name to zip.readBounded(MAX_ZIP_ENTRY)
            }
        }
        val base = RecipePackage.findBase(entries.map { it.first })
        return if (base != null) readPackage(sourceName, base, entries) else readBackup(sourceName, entries)
    }

    /** Rezept-Paket: rezepte.json + fotos/<id>.<ext>; Fotos werden nach dem Import zugeordnet. */
    private fun readPackage(sourceName: String, base: String, entries: List<Pair<String, ByteArray?>>): ImportBatch {
        val json = entries.first { it.first == base + RecipePackage.RECIPES_FILE }.second
            ?: return ImportBatch(emptyList(), listOf(ImportProblem("$sourceName/${RecipePackage.RECIPES_FILE}", "Datei ist größer als 20 MB")))
        val batch = readJson("$sourceName/${RecipePackage.RECIPES_FILE}", json.decodeToString())
        // Ein evtl. vorhandenes photo-Feld gilt im Paket nicht – Fotos kommen aus fotos/
        val recipes = batch.recipes.map { ImportedRecipe(it.recipe.copy(photo = null), null, it.sourceName) }
        val photos = mutableListOf<PackagePhoto>()
        val problems = batch.problems.toMutableList()
        for ((name, data) in entries) {
            if (!name.startsWith(base + RecipePackage.PHOTO_DIR)) continue
            val file = name.removePrefix(base + RecipePackage.PHOTO_DIR)
            val label = "$sourceName/${RecipePackage.PHOTO_DIR}$file"
            val ext = file.substringAfterLast('.', "").lowercase()
            when {
                '/' in file -> problems += ImportProblem(label, "Unterordner in fotos/ werden nicht unterstützt")
                ext !in RecipePackage.PHOTO_EXTENSIONS -> problems += ImportProblem(label, "Kein unterstütztes Bildformat (jpg, jpeg, png, webp)")
                data == null -> problems += ImportProblem(label, "Datei ist größer als 20 MB")
                else -> photos += PackagePhoto(sourceName, file, file.substringBeforeLast('.'), data)
            }
        }
        return ImportBatch(
            recipes, problems,
            packagePhotos = photos,
            packageRecipeIds = mapOf(sourceName to recipes.mapTo(LinkedHashSet()) { it.recipe.id }),
        )
    }

    /** Backup aus „Alle exportieren“: Ordner rezepte (JSON), Ordner fotos (über das photo-Feld), kueche.json. */
    private fun readBackup(sourceName: String, entries: List<Pair<String, ByteArray?>>): ImportBatch {
        val jsons = mutableListOf<Pair<String, String>>()
        val photos = mutableMapOf<String, ByteArray>()
        var kitchen: KitchenState? = null
        for ((name, data) in entries) {
            data ?: continue
            when {
                name == RecipeExporter.KITCHEN_FILE ->
                    kitchen = runCatching { RecipeJson.json.decodeFromString(KitchenState.serializer(), data.decodeToString()) }.getOrNull()
                name.endsWith(".json", ignoreCase = true) -> jsons += name to data.decodeToString()
                name.startsWith("fotos/") -> photos[name.removePrefix("fotos/")] = data
            }
        }
        if (jsons.isEmpty()) {
            return ImportBatch(emptyList(), listOf(ImportProblem(sourceName, "Das Archiv enthält weder rezepte.json noch Rezepte im Ordner rezepte/")))
        }
        val recipes = jsons.fold(ImportBatch.EMPTY) { acc, (name, text) ->
            val batch = readJson("$sourceName/${name.substringAfterLast('/')}", text)
            acc + ImportBatch(
                batch.recipes.map { ImportedRecipe(it.recipe, it.recipe.photo?.let(photos::get), it.sourceName) },
                batch.problems,
            )
        }
        return ImportBatch(recipes.recipes, recipes.problems, kitchen)
    }

    fun validate(recipe: Recipe): String? {
        val major = recipe.format.substringAfter('/', "1").toIntOrNull()
        return when {
            !recipe.format.startsWith("kochbuch-rezept") -> "Unbekanntes Format „${recipe.format}“"
            major != null && major > 1 -> "Formatversion ${recipe.format} ist neuer als diese App"
            recipe.title.isBlank() -> "Titel fehlt"
            recipe.ingredients.none { it.name.isNotBlank() } && recipe.steps.none { it.text.isNotBlank() } ->
                "Weder Zutaten noch Zubereitungsschritte vorhanden"
            else -> null
        }
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    private fun ZipInputStream.readBounded(limit: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun Exception.shortMessage(): String =
        (message ?: javaClass.simpleName).lineSequence().first().take(160)
}
