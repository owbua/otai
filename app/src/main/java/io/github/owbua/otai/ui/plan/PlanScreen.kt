package io.github.owbua.otai.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.data.model.PlanEntry
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.domain.dayLabel
import io.github.owbua.otai.domain.servingsLabel
import io.github.owbua.otai.domain.weekLabel
import io.github.owbua.otai.domain.weekdayShort
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.RecipePickerSheet
import io.github.owbua.otai.ui.components.RecipeRowContent
import io.github.owbua.otai.ui.components.ServingsDialog
import io.github.owbua.otai.ui.theme.RobotoFlexWide
import java.io.File
import java.time.LocalDate

class PlanActions(
    val onOpenRecipe: (String) -> Unit = {},
    val onPick: (LocalDate, Recipe) -> Unit = { _, _ -> },
    val onSwap: (PlanEntry, Recipe) -> Unit = { _, _ -> },
    val onSuggest: (LocalDate) -> Unit = {},
    val onSuggestAnother: (PlanEntry) -> Unit = {},
    val onRemove: (PlanEntry) -> Unit = {},
    val onServings: (PlanEntry, Int) -> Unit = { _, _ -> },
    val onCooked: (PlanEntry, Boolean) -> Unit = { _, _ -> },
    val onOpenShopping: () -> Unit = {},
)

private sealed interface PickTarget {
    data class Day(val date: LocalDate) : PickTarget
    data class Swap(val entry: PlanEntry) : PickTarget
}

@Composable
fun PlanContent(state: PlanUiState, photoFile: (String?) -> File?, actions: PlanActions) {
    val c = MaterialTheme.colorScheme
    var pick by remember { mutableStateOf<PickTarget?>(null) }
    var servingsFor by remember { mutableStateOf<PlanEntry?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, top = 32.dp, bottom = 8.dp)) {
                Text("otai", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.4.sp), color = c.onSurfaceVariant)
                Text("Wochenplan", style = MaterialTheme.typography.displayMedium)
                if (!state.loading) {
                    SummaryRow(state, actions.onOpenShopping)
                }
            }
        }
        if (!state.loading && state.recipes.isEmpty()) {
            item {
                Text(
                    "Importiere zuerst ein paar Rezepte – dann kannst du sie hier auf die Tage verteilen.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        state.weeks.forEach { week ->
            item(key = "w${week.monday}") {
                Text(
                    weekLabel(week.monday, state.today),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(week.days, key = { it.date.toString() }) { day ->
                DayCard(
                    day = day,
                    today = state.today,
                    hasRecipes = state.recipes.isNotEmpty(),
                    photoFile = photoFile,
                    actions = actions,
                    onPick = { pick = PickTarget.Day(day.date) },
                    onSwap = { pick = PickTarget.Swap(it) },
                    onServings = { servingsFor = it },
                )
            }
        }
    }

    pick?.let { target ->
        RecipePickerSheet(
            title = when (target) {
                is PickTarget.Day -> dayLabel(target.date, state.today)
                is PickTarget.Swap -> "Rezept tauschen"
            },
            recipes = state.recipes,
            photoFile = photoFile,
            onDismiss = { pick = null },
            onPick = { r ->
                when (target) {
                    is PickTarget.Day -> actions.onPick(target.date, r)
                    is PickTarget.Swap -> actions.onSwap(target.entry, r)
                }
                pick = null
            },
        )
    }
    servingsFor?.let { e ->
        ServingsDialog(e.servings, onDismiss = { servingsFor = null }) { actions.onServings(e, it); servingsFor = null }
    }
}

@Composable
private fun SummaryRow(state: PlanUiState, onOpenShopping: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("${state.plannedCount}", fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 22.sp)
            Text(if (state.plannedCount == 1) "Gericht geplant" else "Gerichte geplant", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.tertiaryContainer).clickable(onClick = onOpenShopping)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${state.openShopping}", fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 22.sp, color = c.onTertiaryContainer)
                Icon(KIcons.Cart, null, tint = c.onTertiaryContainer, modifier = Modifier.size(18.dp))
            }
            Text("auf der Einkaufsliste", style = MaterialTheme.typography.labelMedium, color = c.onTertiaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayCard(
    day: PlanDayUi,
    today: LocalDate,
    hasRecipes: Boolean,
    photoFile: (String?) -> File?,
    actions: PlanActions,
    onPick: () -> Unit,
    onSwap: (PlanEntry) -> Unit,
    onServings: (PlanEntry) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val isToday = day.date == today
    val past = day.date.isBefore(today)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isToday) c.primaryContainer.copy(alpha = 0.55f) else c.surfaceContainerLow)
            .alpha(if (past) 0.7f else 1f)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Datums-Badge: heute als Cookie-Shape
        Box(
            Modifier
                .size(52.dp)
                .clip(if (isToday) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(16.dp))
                .background(if (isToday) c.primary else c.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weekdayShort(day.date), style = MaterialTheme.typography.labelMedium, color = if (isToday) c.onPrimary else c.onSurfaceVariant)
                Text("${day.date.dayOfMonth}", fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 18.sp, lineHeight = 20.sp, color = if (isToday) c.onPrimary else c.onSurface)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(day.date, today), style = MaterialTheme.typography.titleSmall, color = c.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 2.dp))
                if (day.items.isNotEmpty() && !past && hasRecipes) {
                    IconButton(onClick = onPick, modifier = Modifier.size(32.dp)) {
                        Icon(KIcons.Add, contentDescription = "Weiteres Rezept für ${dayLabel(day.date, today)}", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
            day.items.forEach { item -> PlanItemRow(item, photoFile, actions, onSwap, onServings) }
            if (day.items.isEmpty() && hasRecipes) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPick, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(40.dp)) {
                        Icon(KIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rezept")
                    }
                    FilledTonalButton(
                        onClick = { actions.onSuggest(day.date) },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer),
                    ) {
                        Icon(KIcons.Sparkle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Vorschlag")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanItemRow(
    item: PlanItemUi,
    photoFile: (String?) -> File?,
    actions: PlanActions,
    onSwap: (PlanEntry) -> Unit,
    onServings: (PlanEntry) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val e = item.entry
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).clickable { actions.onOpenRecipe(item.recipe.id) }
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).alpha(if (e.cooked) 0.6f else 1f)) {
                RecipeRowContent(
                    item.recipe, photoFile(item.recipe.photo),
                    subtitle = servingsLabel(e.servings) + if (e.cooked) " · gekocht" else "",
                    size = 52,
                )
            }
            if (e.cooked) Icon(KIcons.Check, contentDescription = "Gekocht", tint = c.primary, modifier = Modifier.size(20.dp))
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(KIcons.MoreVert, contentDescription = "Optionen für ${item.recipe.title}", tint = c.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("Portionen ändern") }, leadingIcon = { Icon(KIcons.Edit, null) }, onClick = { menu = false; onServings(e) })
                    DropdownMenuItem(text = { Text("Rezept tauschen") }, leadingIcon = { Icon(KIcons.Swap, null) }, onClick = { menu = false; onSwap(e) })
                    DropdownMenuItem(text = { Text("Anderer Vorschlag") }, leadingIcon = { Icon(KIcons.Sparkle, null) }, onClick = { menu = false; actions.onSuggestAnother(e) })
                    DropdownMenuItem(
                        text = { Text(if (e.cooked) "Doch nicht gekocht" else "Als gekocht markieren") },
                        leadingIcon = { Icon(KIcons.Check, null) },
                        onClick = { menu = false; actions.onCooked(e, !e.cooked) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Aus dem Plan entfernen") }, leadingIcon = { Icon(KIcons.Delete, null) }, onClick = { menu = false; actions.onRemove(e) })
                }
            }
        }
        e.reason?.takeIf { !e.cooked }?.let { reason ->
            Row(
                Modifier.padding(start = 66.dp, end = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(KIcons.Sparkle, null, tint = c.tertiary, modifier = Modifier.size(14.dp))
                Text(reason, style = MaterialTheme.typography.bodySmall, color = c.tertiary)
            }
        }
    }
}
