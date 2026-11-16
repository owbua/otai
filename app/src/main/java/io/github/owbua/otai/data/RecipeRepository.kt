package io.github.owbua.otai.data

import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.io.ImportedRecipe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

enum class ConflictMode { REPLACE, COPY, SKIP }

data class ImportSummary(
    val added: Int,
    val replaced: Int,
    val skipped: Int,
    val ids: List<String> = emptyList(),
    /** Ursprüngliche ID → gespeicherte ID (bei „Als Kopie“ neu); übersprungene fehlen. */
    val idMap: Map<String, String> = emptyMap(),
)

class RecipeRepository(
    private val store: RecipeStore,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val _recipes = MutableStateFlow<List<Recipe>?>(null)

    /** null, solange noch geladen wird. */
    val recipes: StateFlow<List<Recipe>?> = _recipes.asStateFlow()

    init {
        scope.launch(io) { _recipes.value = store.loadAll() }
    }

    fun recipe(id: String): Flow<Recipe?> =
        recipes.filterNotNull().map { list -> list.find { it.id == id } }.distinctUntilChanged()

    suspend fun get(id: String): Recipe? = recipes.filterNotNull().first().find { it.id == id }

    suspend fun all(): List<Recipe> = recipes.filterNotNull().first()

    /** Foto-Datei, nur wenn sie existiert (Dateizugriff – nicht beim Zeichnen verwenden). */
    fun photoFile(name: String?): File? = name?.let(store::photoFile)?.takeIf { it.isFile }

    /** Foto-Pfad ohne Dateizugriff – für die Oberfläche; fehlende Dateien erkennt das Laden im Hintergrund. */
    fun photoPath(name: String?): File? = name?.let(store::photoFile)

    suspend fun update(id: String, transform: (Recipe) -> Recipe) = mutex.withLock {
        val current = all().find { it.id == id } ?: return@withLock
        val updated = transform(current).normalized().copy(id = id, updatedAt = Instant.now().toString())
        withContext(io) { store.save(updated) }
        _recipes.value = all().map { if (it.id == id) updated else it }
    }

    suspend fun delete(id: String) = mutex.withLock {
        val current = all().find { it.id == id } ?: return@withLock
        withContext(io) { store.delete(current) }
        _recipes.value = all().filterNot { it.id == id }
    }

    suspend fun setPhoto(id: String, jpeg: ByteArray) {
        val old = get(id)?.photo
        val name = withContext(io) { store.writePhoto(id, jpeg) }
        update(id) { it.copy(photo = name) }
        if (old != null) withContext(io) { store.photoFile(old)?.delete() }
    }

    suspend fun removePhoto(id: String) {
        val old = get(id)?.photo ?: return
        update(id) { it.copy(photo = null) }
        withContext(io) { store.photoFile(old)?.delete() }
    }

    fun conflicts(items: List<ImportedRecipe>): List<ImportedRecipe> {
        val ids = _recipes.value.orEmpty().mapTo(HashSet()) { it.id }
        return items.filter { it.recipe.id in ids }
    }

    suspend fun import(items: List<ImportedRecipe>, mode: ConflictMode): ImportSummary = mutex.withLock {
        val now = Instant.now().toString()
        val list = all().toMutableList()
        var added = 0
        var replaced = 0
        var skipped = 0
        val ids = mutableListOf<String>()
        val idMap = LinkedHashMap<String, String>()
        for (item in items) {
            val originalId = item.recipe.id
            val existingIndex = list.indexOfFirst { it.id == item.recipe.id }
            var recipe = item.recipe.copy(photo = null, updatedAt = now)
            if (existingIndex >= 0) {
                when (mode) {
                    ConflictMode.SKIP -> { skipped++; continue }
                    ConflictMode.COPY -> recipe = recipe.copy(id = newRecipeId(recipe.title), createdAt = now)
                    ConflictMode.REPLACE -> recipe = recipe.copy(
                        createdAt = list[existingIndex].createdAt,
                        photo = if (item.photo == null) list[existingIndex].photo else null,
                    )
                }
            } else if (recipe.createdAt == null) {
                recipe = recipe.copy(createdAt = now)
            }
            if (item.photo != null) {
                val name = withContext(io) { store.writePhoto(recipe.id, item.photo) }
                recipe = recipe.copy(photo = name)
            }
            withContext(io) { store.save(recipe) }
            ids += recipe.id
            idMap[originalId] = recipe.id
            val idx = list.indexOfFirst { it.id == recipe.id }
            if (idx >= 0) {
                val oldPhoto = list[idx].photo
                if (oldPhoto != null && oldPhoto != recipe.photo) withContext(io) { store.photoFile(oldPhoto)?.delete() }
                list[idx] = recipe; replaced++
            } else {
                list += recipe; added++
            }
        }
        _recipes.value = list
        ImportSummary(added, replaced, skipped, ids, idMap)
    }
}
