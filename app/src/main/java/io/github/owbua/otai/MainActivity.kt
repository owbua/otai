package io.github.owbua.otai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.owbua.otai.io.web.WebFetcher
import io.github.owbua.otai.ui.ImportExportViewModel
import io.github.owbua.otai.ui.OtaiApp
import io.github.owbua.otai.ui.theme.OtaiTheme

class MainActivity : ComponentActivity() {
    private val importExport: ImportExportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            OtaiTheme {
                OtaiApp(importExport)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** „Öffnen mit“ / „Teilen an“ otai: Datei direkt importieren. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamExtras()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) { importExport.importUris(uris); return }
        // Geteilter Text: JSON (z. B. aus der Claude-App) oder ein Link (z. B. aus dem Browser)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
        when {
            text.startsWith("{") || text.startsWith("[") || text.startsWith("```") -> importExport.importJsonText(text)
            WebFetcher.findUrl(text) != null -> importExport.importFromUrl(text)
        }
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)

    private fun Intent.streamExtras(): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else @Suppress("DEPRECATION") getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
}
