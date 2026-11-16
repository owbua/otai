package io.github.owbua.otai

import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.normalized
import java.io.File

object TestData {
    private fun exampleFile(name: String): File {
        val candidates = listOf(File("../rezept-format/beispiele/$name"), File("rezept-format/beispiele/$name"))
        return candidates.first { it.isFile }
    }

    fun exampleText(name: String) = exampleFile(name).readText()
    fun example(name: String): Recipe = RecipeJson.decode(exampleText(name)).normalized()

    /** Beispiel-Rezept-Paket (rezepte.json + fotos/<id>.jpg); Fotos werden in ein Temp-Verzeichnis entpackt. */
    val zipWithPhotos by lazy {
        val zip = listOf(File("../rezept-format/beispiele/beispiele-mit-fotos.zip"), File("rezept-format/beispiele/beispiele-mit-fotos.zip")).first { it.isFile }
        val batch = io.github.owbua.otai.io.RecipeImporter.read(zip.name, zip.readBytes())
        val dir = kotlin.io.path.createTempDirectory("fotos").toFile()
        batch.recipes.associate { item ->
            val photo = batch.packagePhotos.single { it.recipeId == item.recipe.id }
            val f = File(dir, photo.fileName).apply { writeBytes(photo.bytes) }
            item.recipe.id to (item.recipe.copy(photo = photo.fileName) to f)
        }
    }

    val bauerntopf by lazy { example("bauerntopf.json") }
    val hackPilz by lazy { example("hack-pilz-pfanne.json") }
}
