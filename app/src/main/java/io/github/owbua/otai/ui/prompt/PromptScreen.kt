package io.github.owbua.otai.ui.prompt

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.domain.KiPrompt
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.ShapeBadge
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.trailingShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lädt den Prompt aus den Assets (beim Build aus rezept-format/claude-prompt.md übernommen). */
@Composable
fun rememberKiPrompt(): KiPrompt? {
    val context = LocalContext.current
    val state by produceState<KiPrompt?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("claude-prompt.md").bufferedReader().use { KiPrompt.parse(it.readText()) } }.getOrNull()
        }
    }
    return state
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) }
    }
    cm.setPrimaryClip(clip)
}

@Composable
fun PromptScreen(prompt: KiPrompt?, onBack: () -> Unit, onCopied: (String) -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var variant by rememberSaveable { mutableIntStateOf(0) } // 0 = JSON, 1 = Rezept-Paket
    var copiedTick by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val text = when {
        prompt == null -> ""
        variant == 0 -> prompt.main
        else -> prompt.withPackage
    }
    val copy = {
        if (text.isNotEmpty()) {
            copyToClipboard(context, "KI-Prompt für otai", text)
            copiedTick++
            // Ab Android 13 bestätigt das System selbst
            if (Build.VERSION.SDK_INT < 33) onCopied("Prompt kopiert")
        }
    }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copiedTick) {
        if (copiedTick > 0) { copied = true; delay(2000); copied = false }
    }

    Column(
        Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(KIcons.ArrowBack, contentDescription = "Zurück") }
        }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("KI-Prompt", style = MaterialTheme.typography.displaySmall)
            Text(
                "Mit diesem Prompt wandelt Claude ein Rezept aus PDF, Foto oder Text in das otai-Format um.",
                style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
            )
        }

        // Variante wählen (verbundene Buttons)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Ohne Fotos", "Mit Fotos").forEachIndexed { i, label ->
                val selected = variant == i
                Button(
                    onClick = { variant = i },
                    shape = if (i == 0) leadingShape(24.dp, 8.dp) else trailingShape(24.dp, 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) c.primary else c.secondaryContainer,
                        contentColor = if (selected) c.onPrimary else c.onSecondaryContainer,
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (selected) { Icon(KIcons.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(label, fontWeight = FontWeight(700), maxLines = 1)
                }
            }
        }

        // Antippbare Prompt-Karte
        val bg by animateColorAsState(if (copied) c.tertiaryContainer else c.surfaceContainer, label = "copyBg")
        val fg = if (copied) c.onTertiaryContainer else c.onSurface
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(bg)
                .clickable(role = Role.Button, onClickLabel = "Prompt kopieren", onClick = copy)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (copied) KIcons.Check else KIcons.Copy, null, tint = if (copied) c.onTertiaryContainer else c.primary)
                Text(
                    if (copied) "Kopiert – jetzt in Claude einfügen" else "Antippen zum Kopieren",
                    style = MaterialTheme.typography.titleMedium, color = if (copied) c.onTertiaryContainer else c.primary,
                )
            }
            Box {
                Text(
                    text.ifEmpty { "Prompt wird geladen …" },
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = fg,
                    maxLines = if (expanded) Int.MAX_VALUE else 14,
                )
                if (!expanded) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg))),
                    )
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
            Text(if (expanded) "Weniger anzeigen" else "Ganzen Prompt anzeigen")
        }

        // So geht's
        Column(Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("So geht's", style = MaterialTheme.typography.headlineSmall)
            StepRow(1, "Karte oben antippen – der Prompt liegt in der Zwischenablage.")
            StepRow(2, "In der Claude-App einfügen und das Rezept anhängen (PDF, Foto oder Text).")
            if (variant == 0) {
                StepRow(3, "Claudes Antwort über Teilen an otai schicken – oder als .json speichern und über ⋮ → Einstellungen → Datei importieren laden.")
            } else {
                StepRow(3, "Claude erstellt eine ZIP mit Rezepten und Fotos (dafür muss in Claude die Datei-Erstellung aktiv sein). Die ZIP über Teilen an otai schicken oder über ⋮ → Einstellungen → Datei importieren laden.")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = copy, enabled = text.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(KIcons.Copy, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Prompt kopieren", fontWeight = FontWeight(700))
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(24.dp))
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ShapeBadge("$n", size = 36.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp))
    }
}
