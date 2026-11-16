package io.github.owbua.otai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.owbua.otai.data.Settings
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.trailingShape

class SettingsActions(
    val onBack: () -> Unit = {},
    val onMaxMissing: (Int) -> Unit = {},
    val onMinResults: (Int) -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onImportUrl: () -> Unit = {},
    val onPrompt: () -> Unit = {},
    val onExportAll: () -> Unit = {},
)

fun maxMissingText(n: Int) = when (n) {
    0 -> "Nur Rezepte, für die alles im Vorrat ist."
    1 -> "Rezepte, bei denen höchstens 1 Zutat fehlt."
    else -> "Rezepte, bei denen höchstens $n Zutaten fehlen."
}

/**
 * @param version Versionsname der App (aus dem Paket gelesen)
 * @param exportEnabled false, solange es keine Rezepte gibt
 */
@Composable
fun SettingsContent(settings: Settings, actions: SettingsActions, version: String = "", exportEnabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) { Icon(KIcons.ArrowBack, contentDescription = "Zurück") }
        }
        Text("Einstellungen", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

        Section(KIcons.Dice, "Überrasch mich", "Zufällige Rezepte von chefkoch.de") {
            Text("Fehlende Zutaten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            ConnectedChoice(
                options = (0..Settings.MAX_MISSING_LIMIT).toList(), value = settings.surpriseMaxMissing,
                label = { if (it == 0) "Alles da" else "≤ $it" }, wideFirst = true, onChange = actions.onMaxMissing,
            )
            Text(maxMissingText(settings.surpriseMaxMissing), style = MaterialTheme.typography.bodyLarge)
            Text(
                "Gilt für die Suche „Aus dem Vorrat“. Grundzutaten wie Salz, Pfeffer und Öl sowie Zutaten „nach Belieben“ " +
                    "zählen nie als fehlend; Grundzutaten verwaltest du unter Einkauf → Vorrat.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            Text("Mindestens anzeigen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            ConnectedChoice(
                options = Settings.MIN_RESULTS_OPTIONS, value = settings.surpriseMinResults,
                label = { "$it" }, onChange = actions.onMinResults,
            )
            Text(
                "So viele Rezepte sucht die App pro Durchgang. Mit Vorrat prüft sie dafür bis zu ${maxChecksText(settings.surpriseMinResults)} " +
                    "Rezeptseiten – findet sie weniger Passendes, zeigt sie, was da ist.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
        }

        Section(KIcons.Import, "Import & Export", "Rezepte hinein und hinaus") {
            ActionRow(KIcons.Import, "Datei importieren", "JSON, ZIP-Backup oder Rezept-Paket", onClick = actions.onImportFile)
            ActionRow(KIcons.Link, "Von Webseite importieren", "Link einfügen, z. B. von Chefkoch", onClick = actions.onImportUrl)
            ActionRow(KIcons.Sparkle, "KI-Prompt kopieren", "Rezepte aus PDF oder Foto mit Claude umwandeln", onClick = actions.onPrompt)
            ActionRow(KIcons.Export, "Alle exportieren", "Backup als ZIP – mit Fotos, Wochenplan und Einkauf", enabled = exportEnabled, onClick = actions.onExportAll)
        }

        About(version)
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(24.dp))
    }
}

private fun maxChecksText(minResults: Int) = io.github.owbua.otai.domain.SurpriseFinder.maxChecksFor(minResults, usePantry = true)

@Composable
private fun Section(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerLow)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = c.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Icon(KIcons.ChevronRight, null, tint = c.onSurfaceVariant)
    }
}

/** Über die App – am Fußende */
@Composable
private fun About(version: String) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(KIcons.Book, size = 48.dp)
        Text("otai", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.labelLarge, color = c.primary)
        Text(
            "Rezepte, Fotos, Wochenplan und Einkaufsliste werden nur lokal auf diesem Gerät gespeichert. " +
                "Ins Internet geht die App nur, wenn du etwas von einer Webseite importierst oder „Überrasch mich“ nutzt.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Text(
            "Schrift: Roboto Flex (SIL Open Font License 1.1)",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

/** Verbundene Auswahl-Buttons (M3 Expressive „connected button group“). */
@Composable
private fun <T> ConnectedChoice(options: List<T>, value: T, label: (T) -> String, wideFirst: Boolean = false, onChange: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, option ->
            val selected = option == value
            Button(
                onClick = { onChange(option) },
                shape = when (i) {
                    0 -> leadingShape(24.dp, 8.dp)
                    options.lastIndex -> trailingShape(24.dp, 8.dp)
                    else -> RoundedCornerShape(8.dp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) c.primary else c.secondaryContainer,
                    contentColor = if (selected) c.onPrimary else c.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(if (wideFirst && i == 0) 1.6f else 1f)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton; this.selected = selected },
            ) {
                if (selected && wideFirst && i == 0) { Icon(KIcons.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                Text(label(option), fontWeight = FontWeight(700), maxLines = 1)
            }
        }
    }
}

/** Symbol auf einer Sunny-Form – wie die Formen im übrigen Design. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 44.dp) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(MaterialShapes.Sunny.toShape()).background(c.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.onTertiaryContainer, modifier = Modifier.size(size * 0.5f)) }
}
