package io.github.owbua.otai.ui.overview

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.ui.components.FavoriteBadge
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.RecipeArt
import io.github.owbua.otai.ui.components.sharedRecipeArt
import java.io.File

/** Alle Aktionen der Übersicht – eine stabile Instanz statt vieler einzelner Lambdas. */
@Stable
class OverviewActions(
    val onQuery: (String) -> Unit = {},
    val onFilter: (String) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onSurprise: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onToggleSelect: (String) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onFavoriteSelected: () -> Unit = {},
    val onExportSelected: () -> Unit = {},
    val onShareSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverviewContent(
    state: OverviewState,
    photoFile: (String?) -> File?,
    actions: OverviewActions,
    busy: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val grid = rememberLazyGridState()
    val atTop by remember { derivedStateOf { grid.firstVisibleItemIndex == 0 } }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = state.selecting) { actions.onClearSelection() }

    // Kopf verschwindet beim Runterscrollen und kommt beim Hochscrollen sofort zurück („quick return“)
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val header = remember(grid) {
        // ganz oben scrollt der Kopf einfach mit dem Inhalt, sonst bliebe über der ersten Zeile eine Lücke
        QuickReturnState(nearTop = { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset < headerPx })
    }
    header.maxPx = headerPx.toFloat()

    Box(Modifier.fillMaxSize().background(c.surface).nestedScroll(header)) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp, top = with(density) { headerPx.toDp() } + 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                state.totalCount == 0 -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Noch keine Rezepte",
                        text = "Lege mit „Neues Rezept“ selbst eins an, lass dich über ⋮ → Überrasch mich inspirieren oder importiere unter ⋮ → Einstellungen – als Datei, von einer Webseite wie Chefkoch oder mit dem KI-Prompt umgewandelt.",
                    )
                }
                state.cards.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    val (title, text) = state.emptyHint ?: ("Nichts gefunden" to "Kein Rezept passt zu Suche und Filter.")
                    EmptyState(title = title, text = text)
                }
                else -> items(state.cards, key = { it.id }, contentType = { "recipe" }) { card ->
                    val photo = remember(card.photo) { photoFile(card.photo) }
                    RecipeCard(
                        card = card,
                        photo = photo,
                        selecting = state.selecting,
                        selected = card.id in state.selected,
                        actions = actions,
                    )
                }
            }
        }

        // Kopf als Überlagerung über dem Raster, verschoben um den eingeklappten Teil
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .offset {
                    val y = if (grid.firstVisibleItemIndex == 0) maxOf(header.offsetPx, -grid.firstVisibleItemScrollOffset.toFloat()) else header.offsetPx
                    IntOffset(0, y.roundToInt())
                }
                .background(c.surface)
                .padding(horizontal = 16.dp),
        ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 24.dp)) {
                    Header(actions)
                    SearchField(state.query, actions.onQuery)
                    FilterRow(state.chips, actions.onFilter)
                    if (!state.loading && state.totalCount > 0) {
                        Text(
                            when {
                                state.selecting -> "${state.selected.size} von ${state.cards.size} ausgewählt"
                                state.cards.size == 1 -> "1 Rezept"
                                else -> "${state.cards.size} Rezepte"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.selecting) c.primary else c.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        )
                    }
                    if (busy) {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
        }

        // Statusleiste bleibt ruhig, auch wenn der Kopf eingeklappt ist
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.surface))

        // FAB „Neues Rezept“ – klappt beim Scrollen auf das Icon zusammen
        AnimatedVisibility(
            visible = !state.selecting && !state.loading,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = actions.onNew,
                expanded = atTop,
                icon = { Icon(KIcons.Add, contentDescription = null) },
                text = { Text("Neues Rezept", fontWeight = FontWeight(700)) },
                containerColor = c.primaryContainer,
                contentColor = c.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
            )
        }

        // Mehrfachauswahl: schwebende Werkzeugleiste (M3 Expressive)
        AnimatedVisibility(
            visible = state.selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                leadingContent = {
                    IconButton(onClick = actions.onClearSelection) { Icon(KIcons.Close, contentDescription = "Auswahl beenden") }
                },
            ) {
                IconButton(onClick = actions.onSelectAll) { Icon(KIcons.SelectAll, contentDescription = "Alle auswählen") }
                IconButton(onClick = actions.onFavoriteSelected) { Icon(KIcons.Favorite, contentDescription = "Favorit umschalten") }
                IconButton(onClick = actions.onShareSelected) { Icon(KIcons.Share, contentDescription = "Auswahl teilen") }
                IconButton(onClick = actions.onExportSelected) { Icon(KIcons.Export, contentDescription = "Auswahl exportieren") }
                IconButton(onClick = { confirmDelete = true }) { Icon(KIcons.Delete, contentDescription = "Auswahl löschen") }
            }
        }
    }

    if (confirmDelete) {
        val n = state.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(KIcons.Delete, null) },
            title = { Text(if (n == 1) "1 Rezept löschen?" else "$n Rezepte löschen?") },
            text = { Text("Die Rezepte werden mit Notizen und Fotos entfernt und aus dem Wochenplan genommen. Das lässt sich nicht rückgängig machen.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDeleteSelected() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Header(actions: OverviewActions) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("otai", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.4.sp), color = c.onSurfaceVariant)
            Text("Rezepte", style = MaterialTheme.typography.displayMedium, color = c.onSurface)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            FilledTonalIconButton(
                onClick = { menu = true },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surfaceContainerHigh, contentColor = c.onSurfaceVariant),
            ) { Icon(KIcons.MoreVert, contentDescription = "Weitere Optionen") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                // Import, Export und „Über die App“ liegen in den Einstellungen
                DropdownMenuItem(text = { Text("Einstellungen") }, leadingIcon = { Icon(KIcons.Settings, null) }, onClick = { menu = false; actions.onSettings() })
                DropdownMenuItem(text = { Text("Überrasch mich") }, leadingIcon = { Icon(KIcons.Dice, null) }, onClick = { menu = false; actions.onSurprise() })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Rezepte durchsuchen" },
        decorationBox = { inner ->
            Row(
                Modifier.height(56.dp).background(c.surfaceContainerHighest, RoundedCornerShape(28.dp)).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(KIcons.Search, contentDescription = null, tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Rezepte, Zutaten, Tags …", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(KIcons.Close, contentDescription = "Suche leeren", tint = c.onSurfaceVariant) }
                }
            }
        },
    )
}

@Composable
private fun FilterRow(chips: List<FilterChipUi>, onFilter: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips, key = { it.id }) { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = { onFilter(chip.id) },
                label = { Text(chip.label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (chip.selected) {
                    { Icon(KIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.surface, labelColor = c.onSurfaceVariant,
                    selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary, selectedLeadingIconColor = c.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = chip.selected, borderColor = c.outline, selectedBorderWidth = 0.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(card: RecipeCardUi, photo: File?, selecting: Boolean, selected: Boolean, actions: OverviewActions) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 0.88f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "selScale")
    val radius by animateDpAsState(if (selected) 44.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "selRadius")
    Column(
        Modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
            .combinedClickable(
                role = if (selecting) Role.Checkbox else Role.Button,
                onClickLabel = if (selecting) "Auswahl umschalten" else "Öffnen",
                onLongClickLabel = "Auswählen",
                onClick = { if (selecting) actions.onToggleSelect(card.id) else actions.onOpen(card.id) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onToggleSelect(card.id)
                },
            )
            .semantics {
                if (selecting) {
                    this.selected = selected
                    stateDescription = if (selected) "ausgewählt" else "nicht ausgewählt"
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            RecipeArt(
                id = card.id,
                title = card.title,
                photo = photo,
                shape = RoundedCornerShape(radius),
                modifier = Modifier.matchParentSize().sharedRecipeArt(card.id, photo != null).graphicsLayer { scaleX = scale; scaleY = scale },
                initialSize = 60.sp,
            ) {
                if (card.favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            if (selecting) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (selected) c.primary else c.surface.copy(alpha = 0.85f))
                        .border(2.dp, if (selected) c.primary else c.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(KIcons.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (card.meta.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (card.time != null) Icon(KIcons.Schedule, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(card.meta, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
                }
            }
            if (card.missing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(KIcons.Cart, null, tint = c.primary, modifier = Modifier.size(16.dp))
                    Text("Fehlt: ${card.missing}", style = MaterialTheme.typography.bodySmall, color = c.primary, fontWeight = FontWeight(600), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(KIcons.Restaurant, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(700)), color = MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Verschiebt den Kopf mit jeder Scrollbewegung (runter = einklappen, hoch = sofort wieder zeigen) und
 * rastet nach dem Loslassen ganz ein oder aus. Die Liste scrollt dabei normal weiter.
 */
@Stable
private class QuickReturnState(private val nearTop: () -> Boolean) : NestedScrollConnection {
    var maxPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        offsetPx = (offsetPx + available.y).coerceIn(-maxPx, 0f)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (nearTop()) return Velocity.Zero
        val target = if (offsetPx > -maxPx / 2) 0f else -maxPx
        if (offsetPx != target) {
            androidx.compose.animation.core.animate(offsetPx, target, animationSpec = androidx.compose.animation.core.spring(stiffness = 600f)) { v, _ -> offsetPx = v }
        }
        return Velocity.Zero
    }
}
