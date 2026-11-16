package io.github.owbua.otai.ui.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.data.model.Note
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.domain.amountText
import io.github.owbua.otai.domain.formatMinutes
import io.github.owbua.otai.domain.formatTimerShort
import io.github.owbua.otai.ui.components.InfoChip
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.RecipeArt
import io.github.owbua.otai.ui.components.heroControls
import io.github.owbua.otai.ui.components.sharedRecipeArt
import io.github.owbua.otai.ui.components.SectionTitle
import io.github.owbua.otai.ui.components.ServingsStepper
import io.github.owbua.otai.ui.components.ShapeBadge
import io.github.owbua.otai.ui.components.StatTile
import io.github.owbua.otai.ui.components.TagLabel
import io.github.owbua.otai.ui.components.pressedRadius
import io.github.owbua.otai.ui.theme.RobotoFlexWide
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter

class RecipeActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onLess: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onToggleIngredient: (String) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onPhotoFromSource: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCook: () -> Unit = {},
    val onAddToPlan: () -> Unit = {},
)

@Composable
fun RecipeContent(
    recipe: Recipe,
    photo: File?,
    servings: Int,
    checked: Set<String>,
    actions: RecipeActions,
) {
    val c = MaterialTheme.colorScheme
    val factor = servings.toDouble() / recipe.servings
    var confirmDelete by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    // gleicher Cache-Schlüssel wie das Titelbild → kein doppeltes Laden; antippbar erst, wenn es da ist
    val heroBitmap = io.github.owbua.otai.ui.components.rememberPhoto(photo, 1600)

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp, bottom = 128.dp),
        ) {
            TopRow(recipe, photo != null, actions, onDeleteRequest = { confirmDelete = true })

            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                RecipeArt(
                    id = recipe.id,
                    title = recipe.title,
                    photo = photo,
                    shape = RoundedCornerShape(36.dp),
                    modifier = Modifier
                        .sharedRecipeArt(recipe.id, photo != null)
                        .fillMaxWidth()
                        .height(232.dp)
                        .then(
                            if (heroBitmap != null) Modifier.clip(RoundedCornerShape(36.dp)).clickable(onClickLabel = "Foto vergrößern") { showPhoto = true }
                            else Modifier,
                        ),
                    initialSize = 128.sp,
                    hero = true,
                )
                // Bedienelemente liegen neben (nicht in) dem fliegenden Bild und blenden mit dem Screen
                Box(Modifier.matchParentSize().heroControls()) {
                    if (heroBitmap != null) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(KIcons.ZoomIn, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(20.dp)) }
                    }
                    Button(
                        onClick = actions.onPickPhoto,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).height(40.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.surface, contentColor = c.primary),
                    ) {
                        Icon(if (photo == null) KIcons.Add else KIcons.AddPhoto, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Foto", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.displaySmall)
                if (recipe.description.isNotBlank()) {
                    Text(recipe.description, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                }
            }

            TagRow(recipe)
            Stats(recipe)
            Ingredients(recipe, servings, factor, checked, actions)
            Steps(recipe)
            Tips(recipe)
            NutritionSection(recipe)
            Notes(recipe.notes)
            SourceSection(recipe)
        }

        BottomCookBar(actions.onCook, Modifier.align(Alignment.BottomCenter))
    }

    if (showPhoto && photo != null) {
        io.github.owbua.otai.ui.components.PhotoViewer(photo, recipe.title, onDismiss = { showPhoto = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(KIcons.Delete, null) },
            title = { Text("Rezept löschen?") },
            text = { Text("„${recipe.title}“ wird mit Notizen und Foto entfernt. Das lässt sich nicht rückgängig machen.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun TopRow(recipe: Recipe, hasPhoto: Boolean, actions: RecipeActions, onDeleteRequest: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) {
            Icon(KIcons.ArrowBack, contentDescription = "Zurück zur Übersicht", tint = c.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val radius by animateDpAsState(if (recipe.favorite) 14.dp else 24.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "favRadius")
            val bg by animateColorAsState(if (recipe.favorite) c.primary else c.surfaceContainerHigh, label = "favBg")
            FilledIconToggleButton(
                checked = recipe.favorite,
                onCheckedChange = { actions.onToggleFavorite() },
                shape = RoundedCornerShape(radius),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = bg, contentColor = c.onSurfaceVariant,
                    checkedContainerColor = bg, checkedContentColor = c.onPrimary,
                ),
            ) {
                Icon(if (recipe.favorite) KIcons.Favorite else KIcons.FavoriteBorder, contentDescription = "Favorit")
            }
            IconButton(onClick = actions.onEdit, modifier = Modifier.size(48.dp)) {
                Icon(KIcons.Edit, contentDescription = "Bearbeiten", tint = c.onSurfaceVariant)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(KIcons.MoreVert, contentDescription = "Weitere Optionen", tint = c.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("Zum Wochenplan") }, leadingIcon = { Icon(KIcons.CalendarAdd, null) }, onClick = { menu = false; actions.onAddToPlan() })
                    DropdownMenuItem(text = { Text("Exportieren") }, leadingIcon = { Icon(KIcons.Export, null) }, onClick = { menu = false; actions.onExport() })
                    DropdownMenuItem(text = { Text("Teilen") }, leadingIcon = { Icon(KIcons.Share, null) }, onClick = { menu = false; actions.onShare() })
                    if (recipe.source?.url?.startsWith("http") == true) {
                        DropdownMenuItem(text = { Text(if (hasPhoto) "Foto von Originalseite ersetzen" else "Foto von Originalseite") }, leadingIcon = { Icon(KIcons.Link, null) }, onClick = { menu = false; actions.onPhotoFromSource() })
                    }
                    if (hasPhoto) {
                        DropdownMenuItem(text = { Text("Foto entfernen") }, leadingIcon = { Icon(KIcons.AddPhoto, null) }, onClick = { menu = false; actions.onRemovePhoto() })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(KIcons.Delete, null) }, onClick = { menu = false; onDeleteRequest() })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(recipe: Recipe) {
    val labels = listOfNotNull(recipe.category) + recipe.tags.filterNot { it.equals(recipe.category, ignoreCase = true) }
    if (labels.isEmpty()) return
    FlowRow(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { labels.forEach { TagLabel(it.replaceFirstChar { c -> c.titlecase() }) } }
}

@Composable
private fun Stats(recipe: Recipe) {
    val stats = buildList {
        formatMinutes(recipe.times.totalMin)?.let { add(it to "Gesamt") }
        formatMinutes(recipe.times.activeMin)?.let { add(it to "Aktiv") }
        if (size < 2) formatMinutes(recipe.times.cookMin)?.let { add(it to "Kochen") }
        recipe.difficulty?.let { add(it to "Aufwand") }
    }.take(3)
    if (stats.isEmpty()) return
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f)) }
        repeat(3 - stats.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Ingredients(recipe: Recipe, servings: Int, factor: Double, checked: Set<String>, actions: RecipeActions) {
    if (recipe.ingredients.isEmpty()) return
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionTitle("Zutaten")
            ServingsStepper(servings, actions.onLess, actions.onMore)
        }
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            var lastGroup: String? = null
            recipe.ingredients.forEach { ing ->
                if (ing.group != null && ing.group != lastGroup) {
                    lastGroup = ing.group
                    Text(
                        ing.group,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                IngredientRow(ing, factor, ing.key in checked) { ing.key?.let(actions.onToggleIngredient) }
            }
        }
    }
}

@Composable
private fun IngredientRow(ing: Ingredient, factor: Double, checked: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val deco = if (checked) TextDecoration.LineThrough else TextDecoration.None
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .background(c.surfaceContainerLow)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked, onCheckedChange = null,
            modifier = Modifier.padding(12.dp).size(20.dp),
            colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.outline),
        )
        Text(
            ing.amountText(factor),
            modifier = Modifier.width(76.dp),
            fontSize = 15.sp, fontWeight = FontWeight(800),
            color = if (checked) c.outline else c.primary,
            textDecoration = deco,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(ing.name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp), color = if (checked) c.outline else c.onSurface, textDecoration = deco)
            ing.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Steps(recipe: Recipe) {
    if (recipe.steps.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp)) {
        SectionTitle("Zubereitung", Modifier.padding(start = 8.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            recipe.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShapeBadge("${i + 1}")
                    Column(Modifier.padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        step.timer?.let {
                            InfoChip(KIcons.Timer, formatTimerShort(it.seconds), c.tertiaryContainer, c.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tips(recipe: Recipe) {
    if (recipe.tips.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Tipps", Modifier.padding(start = 8.dp, bottom = 4.dp))
        recipe.tips.forEach { tip ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(KIcons.Lightbulb, null, tint = c.tertiary, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    tip.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NutritionSection(recipe: Recipe) {
    val n = recipe.nutrition ?: return
    val items = listOfNotNull(
        n.kcal?.let { "${it.roundToInt()} kcal" to "Energie" },
        n.proteinG?.let { "${oneDecimal(it)} g" to "Eiweiß" },
        n.fatG?.let { "${oneDecimal(it)} g" to "Fett" },
        n.carbsG?.let { "${oneDecimal(it)} g" to "Kohlenhydrate" },
    )
    if (items.isEmpty()) return
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp)) {
        SectionTitle("Nährwerte pro Portion", Modifier.padding(start = 8.dp, bottom = 12.dp))
        items.chunked(2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun oneDecimal(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().replace('.', ',')
}

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatDate(iso: String?): String = iso?.let { runCatching { LocalDate.parse(it.take(10)).format(DATE) }.getOrDefault(it) } ?: ""

@Composable
private fun Notes(notes: List<Note>) {
    if (notes.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Notizen", Modifier.padding(start = 8.dp, bottom = 4.dp))
        notes.asReversed().forEach { note ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.secondaryContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatDate(note.date), style = MaterialTheme.typography.labelLarge, color = c.onSecondaryContainer)
                    note.rating?.let { Stars(it) }
                }
                if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.bodyMedium, color = c.onSecondaryContainer)
                note.changes.forEach { ch ->
                    Text("• $ch", style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
fun Stars(rating: Int) {
    Row {
        repeat(5) { i ->
            Icon(
                if (i < rating) KIcons.Star else KIcons.StarBorder, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SourceSection(recipe: Recipe) {
    val c = MaterialTheme.colorScheme
    val s = recipe.source
    val lines = buildList {
        if (recipe.cookedCount > 0) {
            add(KIcons.Restaurant to "${recipe.cookedCount}× gekocht" + (recipe.lastCooked?.let { " · zuletzt ${formatDate(it)}" } ?: ""))
        }
        listOfNotNull(s?.author, s?.name).distinct().joinToString(" · ").takeIf { it.isNotEmpty() }?.let { add(KIcons.Person to it) }
    }
    if (lines.isEmpty() && s?.url == null) return
    val uri = LocalUriHandler.current
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        s?.url?.takeIf { it.startsWith("http") }?.let { url ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(KIcons.Link, null, tint = c.primary, modifier = Modifier.size(18.dp))
                Text("Originalrezept öffnen", style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
        }
    }
}

@Composable
private fun BottomCookBar(onCook: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.surfaceContainerHighest)
        val src = remember { MutableInteractionSource() }
        Button(
            onClick = onCook,
            interactionSource = src,
            shape = RoundedCornerShape(pressedRadius(src, 32.dp, 16.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
                .height(64.dp),
        ) {
            Icon(KIcons.PlayArrow, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("Kochen starten", fontFamily = RobotoFlexWide, fontWeight = FontWeight(700), fontSize = 18.sp)
        }
    }
}
