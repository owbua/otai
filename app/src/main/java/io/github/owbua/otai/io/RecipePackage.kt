package io.github.owbua.otai.io

import java.util.Locale

/** Foto aus einem Rezept-Paket (`fotos/<id>.<ext>`), noch unverarbeitet. */
class PackagePhoto(
    /** Name der ZIP-Datei – Fotos werden nur Rezepten aus demselben Paket zugeordnet. */
    val packageName: String,
    val fileName: String,
    /** Dateiname ohne Endung = erwartete Rezept-ID. */
    val recipeId: String,
    val bytes: ByteArray,
)

/**
 * Rezept-Paket: ZIP mit `rezepte.json` (ein Rezept oder Array, Format kochbuch-rezept/1)
 * und optional `fotos/<id>.jpg|jpeg|png|webp`. Darf auch in einem Oberordner liegen.
 */
object RecipePackage {
    const val RECIPES_FILE = "rezepte.json"
    const val PHOTO_DIR = "fotos/"
    val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Ordnerpräfix des Pakets ("" oder "MeinPaket/"), null wenn kein Paket. */
    fun findBase(entryNames: Collection<String>): String? =
        entryNames.filter { it.substringAfterLast('/') == RECIPES_FILE && !isJunk(it) && it.count { c -> c == '/' } <= 1 }
            .minByOrNull { it.length }
            ?.removeSuffix(RECIPES_FILE)

    /** macOS-Beifang („__MACOSX/…“, „._datei“) und versteckte Dateien. */
    fun isJunk(name: String): Boolean =
        name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")

    data class Assignment(val photo: PackagePhoto, val targetId: String, val originalId: String)

    data class PhotoPlan(val assignments: List<Assignment>, val skipped: List<ImportProblem>)

    /**
     * Ordnet Fotos den importierten Rezepten zu.
     * @param packageRecipeIds Paketname → IDs der Rezepte, die im Paket standen
     * @param idMap ursprüngliche ID → tatsächlich gespeicherte ID (fehlt, wenn übersprungen;
     *              weicht ab, wenn als Kopie importiert)
     */
    fun planPhotos(
        photos: List<PackagePhoto>,
        packageRecipeIds: Map<String, Set<String>>,
        idMap: Map<String, String>,
    ): PhotoPlan {
        val assignments = mutableListOf<Assignment>()
        val skipped = mutableListOf<ImportProblem>()
        val used = mutableSetOf<String>()
        for (photo in photos.sortedBy { it.fileName.lowercase(Locale.ROOT) }) {
            val label = "${photo.packageName}/${PHOTO_DIR}${photo.fileName}"
            val ids = packageRecipeIds[photo.packageName].orEmpty()
            val original = ids.firstOrNull { it == photo.recipeId }
                ?: ids.firstOrNull { it.equals(photo.recipeId, ignoreCase = true) }
            when {
                original == null ->
                    skipped += ImportProblem(label, "Kein Rezept mit der ID „${photo.recipeId}“ im Paket")
                idMap[original] == null ->
                    skipped += ImportProblem(label, "Rezept „$original“ wurde nicht importiert")
                !used.add(original) ->
                    skipped += ImportProblem(label, "Weiteres Foto für „$original“ – nur das erste wird verwendet")
                else -> assignments += Assignment(photo, idMap.getValue(original), original)
            }
        }
        return PhotoPlan(assignments, skipped)
    }
}
