package io.github.owbua.otai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Einheitliche Foto-Aufbereitung für alle Wege ins Rezept (Button „Foto“, Web-Import,
 * Rezept-Pakete): auf max. [MAX_SIZE] px (längste Seite) verkleinern, als JPEG mit
 * Qualität [JPEG_QUALITY] speichern.
 */
object Photos {
    const val MAX_SIZE = 1600
    const val JPEG_QUALITY = 85

    /** Thread für die Aufbereitung; Robolectric-Tests stellen auf den Main-Thread um. */
    @Volatile var dispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Button „Foto“: Bild aus dem Photo Picker. */
    fun loadAsJpeg(context: Context, uri: Uri): ByteArray? = process(
        source = { ImageDecoder.createSource(context.contentResolver, uri) },
        open = { context.contentResolver.openInputStream(uri) },
    )

    /** Bilddaten aus Web-Import oder Rezept-Paket (JPEG/PNG/WebP …). */
    fun jpegFromBytes(bytes: ByteArray): ByteArray? = process(
        source = { ImageDecoder.createSource(ByteBuffer.wrap(bytes)) },
        open = { bytes.inputStream() },
    )

    private fun process(source: () -> ImageDecoder.Source, open: () -> InputStream?): ByteArray? = runCatching {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(source()) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIZE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
            open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return null
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()
}
