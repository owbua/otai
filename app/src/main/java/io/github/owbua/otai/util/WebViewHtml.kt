package io.github.owbua.otai.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume

/**
 * Rückfallebene für Seiten, die einfache HTTP-Abrufe blockieren (Bot-Schutz): lädt die Seite
 * unsichtbar in der System-WebView – wie ein normaler Browser – und liefert das gerenderte HTML.
 */
object WebViewHtml {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(context: Context, url: String, timeoutMs: Long = 30_000): String = withContext(Dispatchers.Main) {
        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.loadsImagesAutomatically = false
        }
        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            // kurz warten, falls die Seite Inhalte per JavaScript nachlädt
                            view.postDelayed({
                                view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                    if (cont.isActive) cont.resume(raw)
                                }
                            }, 800)
                        }
                    }
                    webView.loadUrl(url)
                }
            }.let { raw ->
                // evaluateJavascript liefert einen JSON-String-Literal
                (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
        } finally {
            webView.stopLoading()
            delay(1)
            webView.destroy()
        }
    }

    /**
     * Bild über die WebView laden, wenn der direkte Abruf scheitert (Bot-Schutz des Bild-Servers,
     * Format, das Android nicht lesen kann). Die WebView öffnet die Bildadresse selbst – das Bild ist
     * damit „same origin“ – zeichnet es auf ein Canvas und gibt es als JPEG zurück.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun loadImageJpeg(context: Context, url: String, referer: String?, maxSize: Int = 1600, timeoutMs: Long = 20_000): ByteArray =
        withContext(Dispatchers.Main) {
            val webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
            val js = """
                (function(){var i=document.querySelector('img');
                if(!i||!i.complete||!i.naturalWidth) return '';
                var s=Math.min(1,$maxSize/Math.max(i.naturalWidth,i.naturalHeight));
                var c=document.createElement('canvas');
                c.width=Math.max(1,Math.round(i.naturalWidth*s)); c.height=Math.max(1,Math.round(i.naturalHeight*s));
                var g=c.getContext('2d'); g.fillStyle='#fff'; g.fillRect(0,0,c.width,c.height);
                g.drawImage(i,0,0,c.width,c.height);
                try{return c.toDataURL('image/jpeg',0.85);}catch(e){return '';}})()
            """.trimIndent()
            try {
                webView.webViewClient = WebViewClient()
                if (referer != null) webView.loadUrl(url, mapOf("Referer" to referer)) else webView.loadUrl(url)
                withTimeout(timeoutMs) {
                    while (true) {
                        delay(500)
                        val raw = suspendCancellableCoroutine { cont -> webView.evaluateJavascript(js) { if (cont.isActive) cont.resume(it) } }
                        val data = (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                        if (data.startsWith("data:image/jpeg;base64,")) {
                            return@withTimeout java.util.Base64.getDecoder().decode(data.substringAfter(","))
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") ByteArray(0)
                }
            } finally {
                webView.stopLoading()
                delay(1)
                webView.destroy()
            }
        }
}
