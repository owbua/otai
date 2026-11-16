package io.github.owbua.otai.data

import io.github.owbua.otai.data.model.Recipe
import java.io.File

/**
 * Speichert jedes Rezept als eigene JSON-Datei – im selben Format wie Import/Export.
 * Fotos liegen als JPEG daneben.
 */
class RecipeStore(baseDir: File) {
    private val recipesDir = File(baseDir, "recipes").apply { mkdirs() }
    val photosDir = File(baseDir, "photos").apply { mkdirs() }

    fun loadAll(): List<Recipe> =
        recipesDir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching { RecipeJson.decode(file.readText()).normalized() }.getOrNull()
        }

    fun save(recipe: Recipe) {
        val target = fileFor(recipe.id)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(RecipeJson.encode(recipe))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun delete(recipe: Recipe) {
        fileFor(recipe.id).delete()
        recipe.photo?.let { photoFile(it)?.delete() }
    }

    fun photoFile(name: String): File? {
        if (name.contains('/') || name.contains("..")) return null
        return File(photosDir, name)
    }

    fun writePhoto(recipeId: String, bytes: ByteArray, extension: String = "jpg"): String {
        val safeId = slugify(recipeId)
        val name = "$safeId-${System.currentTimeMillis()}.$extension"
        File(photosDir, name).writeBytes(bytes)
        return name
    }

    private fun fileFor(id: String) = File(recipesDir, slugify(id) + "-" + id.hashCode().toUInt().toString(16) + ".json")
}
