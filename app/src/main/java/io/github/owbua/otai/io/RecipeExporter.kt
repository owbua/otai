package io.github.owbua.otai.io

import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.data.model.KitchenState
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.slugify
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Schreibt Rezepte als einzelne JSON-Datei oder als ZIP-Backup (Ordner „rezepte“ mit JSON-Dateien und „fotos“). */
object RecipeExporter {

    fun jsonFileName(recipe: Recipe) = slugify(recipe.title) + ".json"

    fun writeJson(out: OutputStream, recipe: Recipe) {
        // Einzeldatei enthält kein Foto → Verweis entfernen
        out.write(RecipeJson.encode(recipe.copy(photo = null)).encodeToByteArray())
    }

    fun writeZip(out: OutputStream, recipes: List<Recipe>, photoFile: (String) -> File?, kitchen: KitchenState? = null) {
        ZipOutputStream(out).use { zip ->
            val used = mutableSetOf<String>()
            for (recipe in recipes.sortedBy { it.title.lowercase() }) {
                var name = slugify(recipe.title)
                var i = 2
                while (!used.add(name)) name = slugify(recipe.title) + "-" + i++
                zip.putNextEntry(ZipEntry("rezepte/$name.json"))
                zip.write(RecipeJson.encode(recipe).encodeToByteArray())
                zip.closeEntry()
                val photo = recipe.photo?.let(photoFile)?.takeIf { it.isFile }
                if (photo != null) {
                    zip.putNextEntry(ZipEntry("fotos/${recipe.photo}"))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            if (kitchen != null) {
                zip.putNextEntry(ZipEntry(KITCHEN_FILE))
                zip.write(RecipeJson.json.encodeToString(KitchenState.serializer(), kitchen).encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    const val KITCHEN_FILE = "kueche.json"

    /** Dateiname im Paket nur für unbedenkliche IDs (keine Pfadteile). */
    private val SAFE_ID = Regex("[A-Za-z0-9._-]+")

    /**
     * Rezept-Paket schreiben: `rezepte.json` (Array) und `fotos/<id>.jpg` – genau das Format,
     * das der Paket-Import wieder einliest.
     */
    fun writePackage(out: OutputStream, recipes: List<Recipe>, photoFile: (String) -> File?) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(RecipePackage.RECIPES_FILE))
            zip.write(RecipeJson.encodeList(recipes.map { it.copy(photo = null) }).encodeToByteArray())
            zip.closeEntry()
            for (r in recipes) {
                if (!r.id.matches(SAFE_ID) || r.id.startsWith(".")) continue
                val photo = r.photo?.let(photoFile)?.takeIf { it.isFile } ?: continue
                zip.putNextEntry(ZipEntry(RecipePackage.PHOTO_DIR + r.id + ".jpg"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun packageFileName(recipes: List<Recipe>): String =
        if (recipes.size == 1) slugify(recipes.single().title) + ".zip" else "otai-${recipes.size}-rezepte.zip"
}
