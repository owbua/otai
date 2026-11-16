package io.github.owbua.otai.util

import android.content.Context
import io.github.owbua.otai.io.web.WebFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Lädt ein Bild aus dem Netz als aufbereitetes JPEG (wie über den Button „Foto“):
 * erst direkt mit mehreren Strategien, dann über die WebView als Rückfall.
 */
object WebImageLoader {
    /** WebViews sind schwer – höchstens zwei gleichzeitig. */
    private val browserSlots = Semaphore(2)

    suspend fun loadJpeg(context: Context, url: String, referer: String?): ByteArray {
        var lastError: Exception = IOException("Bild konnte nicht geladen werden")
        val direct = withContext(Dispatchers.IO) {
            for (attempt in 0 until WebFetcher.IMAGE_ATTEMPTS) {
                try {
                    val bytes = WebFetcher.fetchImage(url, referer = referer, attempt = attempt)
                    val format = WebFetcher.sniffImage(bytes)
                    if (format == null) {
                        lastError = IOException("Der Bildserver lieferte kein Bild (vermutlich eine Sperrseite)")
                        continue
                    }
                    Photos.jpegFromBytes(bytes)?.let { return@withContext it }
                    lastError = IOException("Bildformat ${format.uppercase()} kann dieses Gerät nicht lesen")
                } catch (e: Exception) {
                    lastError = e
                }
            }
            null
        }
        if (direct != null) return direct
        val viaBrowser = runCatching { browserSlots.withPermit { WebViewHtml.loadImageJpeg(context, url, referer) } }.getOrNull()
        // noch einmal durch die gemeinsame Aufbereitung (Größe, Qualität)
        return viaBrowser?.let { withContext(Dispatchers.Default) { Photos.jpegFromBytes(it) ?: it } } ?: throw lastError
    }
}
