package io.github.owbua.otai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.RecipeJson
import io.github.owbua.otai.io.web.WebFetcher
import io.github.owbua.otai.ui.ImportExportViewModel
import io.github.owbua.otai.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rezept-Paket durch den echten Import-Controller: Rezepte importieren, Fotos zuordnen,
 * Speicherort/Skalierung/Kompression wie beim Button „Foto“, Zusammenfassung, Konflikt „Als Kopie“.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PackageEndToEndTest {
    private lateinit var app: OtaiApplication

    @Before fun setUp() {
        Photos.dispatcher = Dispatchers.Main // Robolectrics Grafik nur auf dem Main-Thread
        app = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() { Photos.dispatcher = Dispatchers.IO }

    private fun idleUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!cond()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < end) { "Timeout: $what" }
            Thread.sleep(10)
        }
    }

    private fun bigPng(): ByteArray {
        val bmp = Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(154, 69, 33)) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun size(file: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(file.path, it) }.let { it.outWidth to it.outHeight }

    @Test fun `Paket importieren und Fotos wie ueber den Foto-Button speichern`() {
        val png = bigPng()
        val smallJpg = TestData.zipWithPhotos.getValue("hack-pilz-pfanne").second.readBytes()
        val zip = zipOf(
            "rezepte.json" to RecipeJson.encodeList(listOf(TestData.bauerntopf, TestData.hackPilz)).encodeToByteArray(),
            "fotos/bauerntopf.png" to png,
            "fotos/hack-pilz-pfanne.jpg" to smallJpg,
            "fotos/katze.jpg" to smallJpg,
        )
        val file = File(app.cacheDir, "paket.zip").apply { writeBytes(zip) }
        val vm = ImportExportViewModel(app)

        vm.importUris(listOf(Uri.fromFile(file)))
        idleUntil("Zusammenfassung") { vm.state.value.packageResult != null }

        val result = vm.state.value.packageResult!!
        assertEquals(2, result.imported)
        assertEquals(2, result.photosAssigned)
        assertEquals(1, result.skipped.size)
        assertEquals("Kein Rezept mit der ID „katze“ im Paket", result.skipped.single().message)
        assertTrue("kein separater Fehlerdialog", vm.state.value.problems.isEmpty())

        val repo = app.repository
        val bauerntopf = runBlocking { repo.get("bauerntopf") }!!
        val photo = repo.photoFile(bauerntopf.photo)!!
        // gleicher Speicherort wie der Foto-Button (RecipeStore.photosDir = filesDir/photos)
        assertEquals(File(app.filesDir, "photos").canonicalPath, photo.parentFile!!.canonicalPath)
        // gleiche Aufbereitung: JPEG, längste Seite 1600 px, Seitenverhältnis erhalten
        assertEquals("jpeg", WebFetcher.sniffImage(photo.readBytes()))
        assertEquals(1600 to 1000, size(photo))
        // byte-genau dasselbe Ergebnis wie Photos.loadAsJpeg (Button „Foto“) für dieselbe Datei
        val pngFile = File(app.cacheDir, "gross.png").apply { writeBytes(png) }
        assertArrayEquals(Photos.loadAsJpeg(app, Uri.fromFile(pngFile)), photo.readBytes())

        val hack = runBlocking { repo.get("hack-pilz-pfanne") }!!
        assertEquals(1200 to 900, size(repo.photoFile(hack.photo)!!)) // kleiner als 1600 → nicht vergrößert

        // Zweiter Import desselben Pakets → Konfliktdialog; „Als Kopie“: Fotos wandern zur Kopie
        vm.dismissPackageResult()
        vm.importUris(listOf(Uri.fromFile(file)))
        idleUntil("Konfliktdialog") { vm.state.value.pending != null }
        assertEquals(2, vm.state.value.pending!!.conflicts.size)
        vm.resolveConflicts(ConflictMode.COPY)
        idleUntil("Zusammenfassung 2") { vm.state.value.packageResult != null }
        assertEquals(2, vm.state.value.packageResult!!.photosAssigned)
        val all = runBlocking { repo.all() }
        val copies = all.filter { it.title == "Bauerntopf" }
        assertEquals(2, copies.size)
        assertNotEquals(copies[0].photo, copies[1].photo)
        copies.forEach { assertNotNull(repo.photoFile(it.photo)) }

        // Dritter Import mit „Überspringen“ → Rezepte und ihre Fotos werden übersprungen
        vm.dismissPackageResult()
        vm.importUris(listOf(Uri.fromFile(file)))
        idleUntil("Konfliktdialog 2") { vm.state.value.pending != null }
        vm.resolveConflicts(ConflictMode.SKIP)
        idleUntil("Zusammenfassung 3") { vm.state.value.packageResult != null }
        val r3 = vm.state.value.packageResult!!
        assertEquals(0, r3.imported)
        assertEquals(0, r3.photosAssigned)
        assertEquals(5, r3.skipped.size) // 2 Rezepte + 2 zugehörige Fotos + katze.jpg
    }
}
