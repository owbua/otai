package io.github.owbua.otai.ui.surprise

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.RecipeArt
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.trailingShape
import androidx.compose.material3.ButtonDefaults
import io.github.owbua.otai.ui.settings.IconBadge
import io.github.owbua.otai.ui.theme.RobotoFlexWide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SurpriseActions(
    val onBack: () -> Unit = {},
    val onRoll: () -> Unit = {},
    val onUsePantry: (Boolean) -> Unit = {},
    val onImport: (String) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onAddMissing: (String) -> Unit = {},
    val onSettings: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SurpriseContent(state: SurpriseState, actions: SurpriseActions) {
    val c = MaterialTheme.colorScheme
    val loading = state.phase is SurprisePhase.Loading
    Box(Modifier.fillMaxSize().background(c.surface)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head", contentType = "head") { Header(state, actions) }
            when (val p = state.phase) {
                is SurprisePhase.Loading -> item(key = "loading") { LoadingBlock(p) }
                SurprisePhase.EmptyPantry -> item(key = "empty") {
                    Message(
                        "Vorrat ist leer",
                        "Trag unter Einkauf → Vorrat ein, was du zuhause hast – oder lass dir beliebige Rezepte zeigen.",
                        button = "Beliebige Rezepte", onClick = { actions.onUsePantry(false) },
                    )
                }
                is SurprisePhase.Failed -> item(key = "failed") {
                    Message("Hat nicht geklappt", p.message, button = "Nochmal versuchen", onClick = actions.onRoll)
                }
                SurprisePhase.Done -> if (state.hits.isEmpty()) {
                    item(key = "none") {
                        Message(
                            "Nichts Passendes dabei",
                            if (state.usePantry) "${state.checked} Rezepte geprüft – bei keinem war genug im Vorrat. Neu würfeln oder in den Einstellungen mehr fehlende Zutaten erlauben."
                            else "Chefkoch hat gerade keine Treffer geliefert. Neu würfeln probiert andere Suchbegriffe.",
                            button = "Einstellungen", onClick = actions.onSettings,
                        )
                    }
                } else {
                    items(state.hits, key = { it.url }, contentType = { "hit" }) { HitCard(it, actions) }
                    item(key = "foot") {
                        Text(
                            (if (state.hits.size < state.minResults) "Nur ${state.hits.size} von ${state.minResults} gewünschten gefunden · " else "") +
                                "${state.checked} Rezepte von chefkoch.de geprüft",
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                        )
                    }
                }
                SurprisePhase.Idle -> {}
            }
        }
        RollBar(enabled = !loading, onRoll = actions.onRoll, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(state: SurpriseState, actions: SurpriseActions) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) { Icon(KIcons.ArrowBack, contentDescription = "Zurück") }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Überrasch mich", style = MaterialTheme.typography.displaySmall)
                Text(
                    if (state.usePantry) "Zufällige Rezepte von Chefkoch, passend zu deinem Vorrat." else "Zufällige Rezepte von Chefkoch – ganz ohne Blick in den Vorrat.",
                    style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                )
            }
            IconBadge(KIcons.Dice, size = 56.dp)
        }
        ModeToggle(state.usePantry, enabled = state.phase !is SurprisePhase.Loading, onChange = actions.onUsePantry)
        FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.terms.isNotEmpty()) InfoPill(KIcons.Search, "Gesucht: " + state.terms.joinToString(" + "), c.secondaryContainer, c.onSecondaryContainer)
            InfoPill(
                KIcons.Settings,
                if (!state.usePantry) "Mindestens ${state.minResults} Rezepte"
                else when (state.maxMissing) { 0 -> "Alles muss da sein"; 1 -> "Höchstens 1 fehlt"; else -> "Höchstens ${state.maxMissing} fehlen" },
                c.surfaceContainerHigh, c.onSurface, onClick = actions.onSettings,
            )
        }
    }
}

/** Aus dem Vorrat | Beliebig – verbundene Buttons */
@Composable
private fun ModeToggle(usePantry: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(true to "Aus dem Vorrat", false to "Beliebig").forEachIndexed { i, (value, label) ->
            val selected = value == usePantry
            Button(
                onClick = { if (!selected) onChange(value) },
                enabled = enabled || selected,
                shape = if (i == 0) leadingShape(24.dp, 8.dp) else trailingShape(24.dp, 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) c.primary else c.secondaryContainer,
                    contentColor = if (selected) c.onPrimary else c.onSecondaryContainer,
                    disabledContainerColor = if (selected) c.primary else c.secondaryContainer,
                    disabledContentColor = if (selected) c.onPrimary else c.onSecondaryContainer,
                ),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(if (selected) KIcons.Check else if (value) KIcons.Pantry else KIcons.Dice, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight(700), maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(CircleShape).background(bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingBlock(p: SurprisePhase.Loading) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator(Modifier.size(72.dp))
        val pr = p.progress
        Text(
            when {
                pr == null -> "Wähle Zutaten aus dem Vorrat …"
                pr.total == 0 -> "Suche „${pr.terms.joinToString(" + ")}“ bei Chefkoch …"
                else -> "Prüfe Rezepte · ${pr.checked} von ${pr.total}"
            },
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        if (pr != null && pr.total > 0) {
            LinearWavyProgressIndicator(progress = { pr.checked.toFloat() / pr.total }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "Chefkoch prüft Abrufe manchmal auf Bots – dann lädt die App die Seiten wie ein Browser, das dauert etwas länger.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Message(title: String, text: String, button: String? = null, onClick: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(KIcons.Pantry, null, tint = c.primary, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        if (button != null) OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(button) }
    }
}

@Composable
private fun HitCard(hit: HitUi, actions: SurpriseActions) {
    val c = MaterialTheme.colorScheme
    var expanded by rememberSaveable(hit.url) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.surfaceContainerLow)
            .clickable(onClickLabel = if (expanded) "Zutaten ausblenden" else "Zutaten zeigen") { expanded = !expanded }
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WebThumb(hit)
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                if (hit.meta.isNotEmpty()) Text(hit.meta, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
                if (hit.missing.isEmpty()) {
                    StatusPill(KIcons.Check, "Alles da", c.tertiaryContainer, c.onTertiaryContainer)
                } else {
                    StatusPill(KIcons.Cart, "Fehlt: " + hit.missing.joinToString(", "), c.secondaryContainer, c.onSecondaryContainer)
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(color = c.outlineVariant, modifier = Modifier.padding(bottom = 6.dp))
                hit.ingredients.forEach { ing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (ing.missing) c.primary else c.outlineVariant))
                        Text(
                            ing.text + if (ing.missing) " – fehlt" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ing.missing) c.primary else c.onSurface,
                            fontWeight = if (ing.missing) FontWeight(700) else null,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hit.existingId != null) {
                FilledTonalButton(onClick = { actions.onOpen(hit.existingId) }, modifier = Modifier.weight(1f)) {
                    Icon(KIcons.Book, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Öffnen", maxLines = 1)
                }
            } else {
                Button(onClick = { actions.onImport(hit.url) }, modifier = Modifier.weight(1f)) {
                    Icon(KIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ins Kochbuch", maxLines = 1)
                }
            }
            if (hit.missing.isNotEmpty()) {
                OutlinedButton(onClick = { actions.onAddMissing(hit.url) }, enabled = !hit.onList, modifier = Modifier.weight(1f)) {
                    Icon(if (hit.onList) KIcons.Check else KIcons.AddCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hit.onList) "Auf der Liste" else "Auf die Liste", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 2)
    }
}

@Composable
private fun WebThumb(hit: HitUi) {
    val shape = RoundedCornerShape(20.dp)
    val bitmap = rememberWebImage(hit.imageUrl, hit.url)
    if (bitmap != null) {
        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.size(104.dp).clip(shape))
    } else {
        RecipeArt(id = hit.url, title = hit.title, photo = null, shape = shape, modifier = Modifier.size(104.dp), initialSize = 40.sp)
    }
}

/** Vorschaubilder der Funde: als fertiges JPEG gemerkt (für „Ins Kochbuch“) und klein dekodiert. */
object WebImages {
    /** In Screenshot-Tests aus – dort gibt es kein Netz. */
    @Volatile var enabled = true
    private val jpegs = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    internal val bitmaps = LruCache<String, ImageBitmap>(30)

    /** Schon geladenes Foto – spart beim Import den zweiten Download. */
    fun jpeg(url: String?): ByteArray? = url?.let { jpegs.get(it) }
    internal fun put(url: String, jpeg: ByteArray) { jpegs.put(url, jpeg) }
}

@Composable
private fun rememberWebImage(url: String?, referer: String): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by produceState(initialValue = url?.let { WebImages.bitmaps.get(it) }, url) {
        if (url == null || value != null || !WebImages.enabled) return@produceState
        val jpeg = WebImages.jpeg(url) ?: runCatching { io.github.owbua.otai.util.WebImageLoader.loadJpeg(context, url, referer) }.getOrNull()
            ?.also { WebImages.put(url, it) } ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }?.also { WebImages.bitmaps.put(url, it) }
    }
    return state
}

@Composable
private fun RollBar(enabled: Boolean, onRoll: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.surfaceContainerHighest)
        Button(
            onClick = onRoll, enabled = enabled,
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp).height(64.dp),
        ) {
            Icon(KIcons.Dice, null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text("Neu würfeln", fontFamily = RobotoFlexWide, fontWeight = FontWeight(700), fontSize = 18.sp)
        }
    }
}
