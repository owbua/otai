package io.github.owbua.otai.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.owbua.otai.OtaiApplication
import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.ImportSummary
import io.github.owbua.otai.io.ImportBatch
import io.github.owbua.otai.io.ImportProblem
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.io.RecipeExporter
import io.github.owbua.otai.io.RecipeImporter
import io.github.owbua.otai.io.RecipePackage
import io.github.owbua.otai.io.web.JsonLdRecipeParser
import io.github.owbua.otai.io.web.NoRecipeFoundException
import io.github.owbua.otai.io.web.WebRecipe
import io.github.owbua.otai.io.web.WebFetcher
import io.github.owbua.otai.util.Photos
import io.github.owbua.otai.util.WebViewHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class PendingImport(val batch: ImportBatch, val conflicts: List<ImportedRecipe>) {
    val items get() = batch.recipes
}

/** Ergebnis eines Rezept-Paket-Imports für die Zusammenfassung. */
data class PackageResult(
    val imported: Int,
    val replaced: Int,
    val photosAssigned: Int,
    /** Alles, was nicht übernommen wurde – mit Grund. */
    val skipped: List<ImportProblem>,
)

data class ImportExportState(
    val busy: Boolean = false,
    val pending: PendingImport? = null,
    val problems: List<ImportProblem> = emptyList(),
    val packageResult: PackageResult? = null,
)

/** Import und Export – activity-weit, damit auch per „Öffnen mit“ empfangene Dateien landen. */
class ImportExportViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as OtaiApplication).repository
    private val kitchenRepo = (app as OtaiApplication).kitchen
    private val _state = MutableStateFlow(ImportExportState())
    val state = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _share = Channel<Intent>(Channel.BUFFERED)
    val shareIntents = _share.receiveAsFlow()

    /** Nach einem Einzelimport (Web oder geteilter Text) direkt das Rezept öffnen. */
    private val _open = Channel<String>(Channel.BUFFERED)
    val openRecipe = _open.receiveAsFlow()
    private var openAfterImport = false

    /** Rezept von einer Webseite (schema.org/Recipe, z. B. Chefkoch) inkl. Foto importieren. */
    fun importFromUrl(rawUrl: String) {
        val url = WebFetcher.findUrl(rawUrl.trim()) ?: rawUrl.trim().takeIf { it.isNotEmpty() }?.let { "https://$it" }
        if (url == null) { message("Kein gültiger Link"); return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = runCatching {
                val web = parsePage(url)
                val photo = web.imageUrl?.let { img -> runCatching { downloadPhoto(img, url) } }
                if (photo?.isFailure == true) photoWarning = webError(photo.exceptionOrNull()!!)
                ImportedRecipe(web.recipe, photo?.getOrNull(), url)
            }
            _state.update { it.copy(busy = false) }
            result.fold(
                onSuccess = { item ->
                    val error = RecipeImporter.validate(item.recipe)
                    if (error != null) _messages.send("Import nicht möglich: $error")
                    else startImport(ImportBatch(listOf(item), emptyList()), openSingle = true)
                },
                onFailure = { e -> _messages.send(webError(e)) },
            )
        }
    }

    /** Bereits geladenes Web-Rezept (z. B. aus „Überrasch mich“) samt Foto ins Kochbuch übernehmen. */
    fun importWebRecipe(web: io.github.owbua.otai.io.web.WebRecipe, url: String, prefetchedPhoto: ByteArray? = null) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val photo = prefetchedPhoto?.let { Result.success(it) } ?: web.imageUrl?.let { img -> runCatching { downloadPhoto(img, url) } }
            if (photo?.isFailure == true) photoWarning = webError(photo.exceptionOrNull()!!)
            _state.update { it.copy(busy = false) }
            val item = ImportedRecipe(web.recipe, photo?.getOrNull(), url)
            val error = RecipeImporter.validate(item.recipe)
            if (error != null) _messages.send("Import nicht möglich: $error")
            else startImport(ImportBatch(listOf(item), emptyList()), openSingle = true)
        }
    }

    /** Hinweis, wenn das Rezept ohne Foto importiert wurde (wird an die Erfolgsmeldung angehängt). */
    private var photoWarning: String? = null

    /** Bild laden, prüfen und als JPEG aufbereiten; mehrere Strategien, WebView als Rückfall. */
    private suspend fun downloadPhoto(imageUrl: String, pageUrl: String): ByteArray =
        io.github.owbua.otai.util.WebImageLoader.loadJpeg(getApplication(), imageUrl, pageUrl)

    /** JSON-Text, z. B. direkt aus der Claude-App an otai geteilt. */
    fun importJsonText(text: String) = viewModelScope.launch {
        val batch = withContext(Dispatchers.Default) { RecipeImporter.readJson("Geteilter Text", text) }
        _state.update { it.copy(problems = batch.problems) }
        if (batch.recipes.isNotEmpty()) startImport(batch, openSingle = batch.recipes.size == 1)
    }

    /** Foto von der Originalseite (source.url) nachladen. */
    fun loadPhotoFromSource(id: String) = viewModelScope.launch {
        val recipe = repo.get(id) ?: return@launch
        val url = recipe.source?.url ?: run { _messages.send("Keine Quelle hinterlegt"); return@launch }
        _state.update { it.copy(busy = true) }
        val photo = runCatching {
            val img = parsePage(url).imageUrl ?: throw java.io.IOException("Die Seite enthält kein Rezeptfoto")
            downloadPhoto(img, url)
        }
        _state.update { it.copy(busy = false) }
        photo.fold(
            onSuccess = { repo.setPhoto(id, it); _messages.send("Foto geladen") },
            onFailure = { _messages.send(webError(it)) },
        )
    }

    /**
     * Erst direkter Abruf (schnell, ohne JavaScript); blockt die Seite das oder fehlen die Daten,
     * wird sie wie im Browser in einer unsichtbaren WebView geladen.
     */
    private suspend fun parsePage(url: String): WebRecipe {
        val direct = withContext(Dispatchers.IO) { runCatching { JsonLdRecipeParser.parse(WebFetcher.fetchHtml(url), url) } }
        direct.getOrNull()?.let { return it }
        val viaBrowser = runCatching { JsonLdRecipeParser.parse(WebViewHtml.load(getApplication(), url), url) }
        return viaBrowser.getOrElse { e ->
            // aussagekräftigsten Fehler melden
            throw if (e is NoRecipeFoundException || direct.exceptionOrNull() == null) e else direct.exceptionOrNull()!!
        }
    }

    private fun webError(e: Throwable) = when (e) {
        is NoRecipeFoundException -> e.message!!
        is java.net.UnknownHostException -> "Keine Internetverbindung oder unbekannte Adresse"
        is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> "Zeitüberschreitung beim Laden der Seite"
        else -> "Laden fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
    }

    private suspend fun startImport(batch: ImportBatch, openSingle: Boolean) {
        repo.all()
        openAfterImport = openSingle && batch.recipes.size == 1
        val conflicts = repo.conflicts(batch.recipes)
        if (conflicts.isEmpty()) finishImport(batch, ConflictMode.COPY)
        else _state.update { it.copy(pending = PendingImport(batch, conflicts)) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val batch = withContext(Dispatchers.IO) {
                uris.fold(ImportBatch.EMPTY) { acc, uri -> acc + readUri(uri) }
            }
            // Bei Rezept-Paketen erscheinen Probleme in der Zusammenfassung statt im Fehlerdialog
            _state.update { it.copy(busy = false, problems = if (batch.isPackage) emptyList() else batch.problems) }
            if (batch.recipes.isEmpty() && !batch.isPackage) {
                if (batch.problems.isEmpty()) _messages.send("Keine Rezepte gefunden")
                return@launch
            }
            startImport(batch, openSingle = false)
        }
    }

    fun resolveConflicts(mode: ConflictMode?) {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        if (mode == null) { openAfterImport = false; photoWarning = null; return }
        viewModelScope.launch { finishImport(pending.batch, mode) }
    }

    fun dismissProblems() = _state.update { it.copy(problems = emptyList()) }

    fun dismissPackageResult() = _state.update { it.copy(packageResult = null) }

    private suspend fun finishImport(batch: ImportBatch, mode: ConflictMode) {
        _state.update { it.copy(busy = true) }
        val summary = repo.import(batch.recipes, mode)
        batch.kitchen?.let { kitchenRepo.merge(it, repo.all().mapTo(HashSet()) { r -> r.id }) }
        if (batch.isPackage) {
            val result = assignPackagePhotos(batch, summary)
            _state.update { it.copy(busy = false, packageResult = result) }
            openAfterImport = false
            return
        }
        _state.update { it.copy(busy = false) }
        _messages.send(summary.message(batch.problems.size) + (photoWarning?.let { " – ohne Foto: $it" } ?: ""))
        photoWarning = null
        if (openAfterImport && summary.ids.size == 1) _open.send(summary.ids.single())
        openAfterImport = false
    }

    /**
     * Fotos aus Rezept-Paketen nach dem Import über den Dateinamen (= Rezept-ID) zuordnen und
     * genauso speichern wie über den Button „Foto“ ([Photos.jpegFromBytes] → [RecipeRepository.setPhoto]).
     */
    private suspend fun assignPackagePhotos(batch: ImportBatch, summary: ImportSummary): PackageResult {
        val plan = RecipePackage.planPhotos(batch.packagePhotos, batch.packageRecipeIds, summary.idMap)
        val skipped = batch.problems.toMutableList()
        batch.recipes.filter { it.recipe.id !in summary.idMap }.forEach {
            skipped += ImportProblem("Rezept „${it.recipe.title}“", "Existiert bereits – übersprungen")
        }
        skipped += plan.skipped
        var assigned = 0
        for (a in plan.assignments) {
            val jpeg = withContext(Photos.dispatcher) { Photos.jpegFromBytes(a.photo.bytes) }
            if (jpeg == null) {
                val format = WebFetcher.sniffImage(a.photo.bytes)?.uppercase() ?: "unbekanntes Format"
                skipped += ImportProblem("${a.photo.packageName}/${RecipePackage.PHOTO_DIR}${a.photo.fileName}", "Bild konnte nicht gelesen werden ($format)")
            } else {
                repo.setPhoto(a.targetId, jpeg)
                assigned++
            }
        }
        return PackageResult(summary.added + summary.replaced, summary.replaced, assigned, skipped)
    }

    private fun ImportSummary.message(errors: Int): String {
        val parts = buildList {
            val imported = added + replaced
            add(if (imported == 1) "1 Rezept importiert" else "$imported Rezepte importiert")
            if (replaced > 0) add("$replaced ersetzt")
            if (skipped > 0) add("$skipped übersprungen")
            if (errors > 0) add(if (errors == 1) "1 Fehler" else "$errors Fehler")
        }
        return parts.joinToString(", ")
    }

    private fun readUri(uri: Uri): ImportBatch {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Datei"
        return try {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val limit = 100L * 1024 * 1024
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) throw IllegalStateException("Datei ist größer als 100 MB")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return ImportBatch(emptyList(), listOf(ImportProblem(name, "Datei konnte nicht geöffnet werden")))
            RecipeImporter.read(name, bytes)
        } catch (e: Exception) {
            ImportBatch(emptyList(), listOf(ImportProblem(name, e.message ?: "Lesefehler")))
        }
    }

    // --- Export ----------------------------------------------------------------------------------

    fun backupFileName() = "otai-backup-${LocalDate.now()}.zip"

    fun exportAll(target: Uri) = viewModelScope.launch {
        val recipes = repo.all()
        val kitchen = kitchenRepo.current()
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target, "wt")!!.use { out ->
                    RecipeExporter.writeZip(out, recipes, { repo.photoFile(it) }, kitchen)
                }
            }.isSuccess
        }
        _messages.send(if (ok) "${recipes.size} Rezepte exportiert" else "Export fehlgeschlagen")
    }

    fun exportRecipe(id: String, target: Uri) = viewModelScope.launch {
        val recipe = repo.get(id) ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target, "wt")!!.use { out ->
                    RecipeExporter.writeJson(out, recipe)
                }
            }.isSuccess
        }
        _messages.send(if (ok) "„${recipe.title}“ exportiert" else "Export fehlgeschlagen")
    }

    /** Ausgewählte Rezepte als Rezept-Paket (rezepte.json + fotos/<id>.jpg) speichern. */
    fun exportPackage(ids: List<String>, target: Uri) = viewModelScope.launch {
        val recipes = repo.all().filter { it.id in ids }
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target, "wt")!!.use { out ->
                    RecipeExporter.writePackage(out, recipes) { repo.photoFile(it) }
                }
            }.isSuccess
        }
        _messages.send(if (ok) (if (recipes.size == 1) "1 Rezept exportiert" else "${recipes.size} Rezepte exportiert") else "Export fehlgeschlagen")
    }

    suspend fun packageFileName(ids: List<String>) = RecipeExporter.packageFileName(repo.all().filter { it.id in ids })

    /** Ausgewählte Rezepte als Rezept-Paket teilen. */
    fun sharePackage(ids: List<String>) = viewModelScope.launch {
        val recipes = repo.all().filter { it.id in ids }
        if (recipes.isEmpty()) return@launch
        val app = getApplication<Application>()
        val uri = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, RecipeExporter.packageFileName(recipes))
            file.outputStream().use { RecipeExporter.writePackage(it, recipes) { name -> repo.photoFile(name) } }
            FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, if (recipes.size == 1) recipes.single().title else "${recipes.size} Rezepte")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _share.send(Intent.createChooser(send, "Rezepte teilen"))
    }

    fun shareRecipe(id: String) = viewModelScope.launch {
        val recipe = repo.get(id) ?: return@launch
        val app = getApplication<Application>()
        val uri = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, RecipeExporter.jsonFileName(recipe))
            file.outputStream().use { RecipeExporter.writeJson(it, recipe) }
            FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recipe.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _share.send(Intent.createChooser(send, "Rezept teilen"))
    }

    fun recipeFileName(id: String, title: String) = RecipeExporter.jsonFileName(
        io.github.owbua.otai.data.model.Recipe(id = id, title = title),
    )

    fun message(text: String) = viewModelScope.launch { _messages.send(text) }
}
