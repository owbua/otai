package io.github.owbua.otai.io.web

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** Lädt Rezeptseiten und Bilder. Nur auf ausdrücklichen Wunsch (Import / Foto laden). */
object WebFetcher {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun fetchHtml(url: String): String {
        val (bytes, contentType) = get(url, 8L * 1024 * 1024, "text/html,application/xhtml+xml")
        val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType.orEmpty())?.groupValues?.get(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    /**
     * Bild laden. Bewusst ohne AVIF/HEIC im Accept-Header: viele CDNs (auch Chefkoch) liefern sonst
     * AVIF aus, das ältere Android-Versionen nicht dekodieren können. [attempt] wählt die Strategie
     * für Wiederholungsversuche (0 = Browser-ähnlich, 1 = nur JPEG, 2 = schlichter Client).
     */
    fun fetchImage(url: String, referer: String? = null, attempt: Int = 0): ByteArray {
        val accept = when (attempt) {
            0 -> "image/webp,image/jpeg,image/png;q=0.9,image/*;q=0.5"
            1 -> "image/jpeg"
            else -> "*/*"
        }
        return get(url, 15L * 1024 * 1024, accept, referer, browserUa = attempt < 2).first
    }

    const val IMAGE_ATTEMPTS = 3

    private fun get(url: String, limit: Long, accept: String, referer: String? = null, browserUa: Boolean = true): Pair<ByteArray, String?> {
        var current = URL(url)
        repeat(6) {
            if (current.protocol != "https" && current.protocol != "http") throw IOException("Nur http(s)-Links werden unterstützt")
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                if (browserUa) setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", accept)
                if (referer != null) setRequestProperty("Referer", referer)
                setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.5")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel")
                    current = URL(current, loc)
                    return@repeat
                }
                if (code !in 200..299) throw IOException("Server antwortet mit HTTP $code")
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw IOException("Antwort ist zu groß")
                        out.write(buf, 0, n)
                    }
                }
                return out.toByteArray() to conn.contentType
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    /** Bildformat anhand der Datei-Signatur – der Content-Type-Header ist oft falsch. */
    fun sniffImage(b: ByteArray): String? {
        fun at(off: Int, t: String) = b.size >= off + t.length && t.indices.all { b[off + it] == t[it].code.toByte() }
        return when {
            b.size < 12 -> null
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "jpeg"
            b[0] == 0x89.toByte() && at(1, "PNG") -> "png"
            at(0, "GIF8") -> "gif"
            at(0, "RIFF") && at(8, "WEBP") -> "webp"
            at(4, "ftypavif") || at(4, "ftypavis") -> "avif"
            at(4, "ftyp") -> "heic"
            at(0, "BM") -> "bmp"
            else -> null
        }
    }

    /** Erste http(s)-URL in einem Text (z. B. „Schau mal: https://… “ aus dem Teilen-Dialog). */
    fun findUrl(text: String?): String? =
        text?.let { Regex("""https?://[^\s<>"']+""").find(it)?.value?.trimEnd('.', ',', ')', ']') }
}
