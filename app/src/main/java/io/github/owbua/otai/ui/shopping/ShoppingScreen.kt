package io.github.owbua.otai.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.domain.ShoppingLine
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.pressedRadius
import io.github.owbua.otai.ui.components.trailingShape
import io.github.owbua.otai.ui.theme.RobotoFlexWide

class ShoppingActions(
    val onAdd: (String) -> Unit = {},
    val onToggle: (ShoppingLine) -> Unit = {},
    val onRemove: (ShoppingLine) -> Unit = {},
    val onHaveAtHome: (ShoppingLine) -> Unit = {},
    val onCheckout: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onAddPantry: (String) -> Unit = {},
    val onRemovePantry: (String) -> Unit = {},
    val onAddStaple: (String) -> Unit = {},
    val onRemoveStaple: (String) -> Unit = {},
    val onOpenPlan: () -> Unit = {},
)

@Composable
fun ShoppingContent(state: ShoppingUiState, actions: ShoppingActions, initialTab: Int = 0) {
    val c = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Box(Modifier.fillMaxSize().background(c.surface)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (tab == 0 && state.checkedCount > 0) 112.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "header") {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 32.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("otai", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.4.sp), color = c.onSurfaceVariant)
                            Text("Einkauf", style = MaterialTheme.typography.displayMedium)
                        }
                        if (tab == 0) {
                            FilledTonalIconButton(
                                onClick = actions.onShare,
                                enabled = state.openCount > 0,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surfaceContainerHigh, contentColor = c.onSurfaceVariant),
                            ) { Icon(KIcons.Share, contentDescription = "Einkaufsliste teilen") }
                        }
                    }
                    TabSwitch(tab, state.openCount, state.pantry.size) { tab = it }
                }
            }
            if (tab == 0) listTab(state, actions) else pantryTab(state, actions)
        }

        if (tab == 0 && state.checkedCount > 0) {
            val src = remember { MutableInteractionSource() }
            Button(
                onClick = actions.onCheckout,
                interactionSource = src,
                shape = RoundedCornerShape(pressedRadius(src, 28.dp, 14.dp)),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(56.dp),
            ) {
                Icon(KIcons.Pantry, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Gekauftes in den Vorrat (${state.checkedCount})", fontWeight = FontWeight(700), fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TabSwitch(tab: Int, openCount: Int, pantryCount: Int, onTab: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("Liste · $openCount" to KIcons.Cart, "Vorrat · $pantryCount" to KIcons.Pantry).forEachIndexed { i, (label, icon) ->
            val selected = tab == i
            Button(
                onClick = { onTab(i) },
                shape = if (i == 0) leadingShape(24.dp, 8.dp) else trailingShape(24.dp, 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) c.primary else c.secondaryContainer,
                    contentColor = if (selected) c.onPrimary else c.onSecondaryContainer,
                ),
                modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = label },
            ) {
                Icon(if (selected) KIcons.Check else icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight(700))
            }
        }
    }
}

private fun LazyListScope.listTab(state: ShoppingUiState, actions: ShoppingActions) {
    item(key = "add") { AddField("Artikel hinzufügen, z. B. 500 g Mehl", actions.onAdd) }
    if (!state.loading && state.lines.isEmpty()) {
        item(key = "empty") {
            EmptyHint(
                if (state.plannedCount == 0) "Noch nichts zu kaufen. Plane Rezepte im Wochenplan – die Zutaten erscheinen dann automatisch hier."
                else "Alles da! Für deine geplanten Gerichte fehlt nichts.",
                action = if (state.plannedCount == 0) "Zum Wochenplan" to actions.onOpenPlan else null,
            )
        }
    }
    val plan = state.lines.filter { it.fromPlan && !it.checked }
    val manual = state.lines.filter { !it.fromPlan && !it.checked }
    val done = state.lines.filter { it.checked }
    section("Für deinen Wochenplan", plan, actions)
    section("Eigene Artikel", manual, actions)
    section("Im Wagen", done, actions)
}

private fun LazyListScope.section(title: String, lines: List<ShoppingLine>, actions: ShoppingActions) {
    if (lines.isEmpty()) return
    item(key = "h_$title") {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
    }
    lines.forEachIndexed { i, line ->
        item(key = line.key) {
            val shape = when {
                lines.size == 1 -> RoundedCornerShape(20.dp)
                i == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                i == lines.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                else -> RoundedCornerShape(4.dp)
            }
            LineRow(line, shape, actions)
        }
    }
}

@Composable
private fun LineRow(line: ShoppingLine, shape: RoundedCornerShape, actions: ShoppingActions) {
    val c = MaterialTheme.colorScheme
    val deco = if (line.checked) TextDecoration.LineThrough else TextDecoration.None
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceContainerLow)
            .toggleable(value = line.checked, role = Role.Checkbox, onValueChange = { actions.onToggle(line) })
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = line.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp).size(20.dp),
            colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.outline))
        val amount = line.amountText()
        Text(
            amount, modifier = Modifier.width(72.dp), fontSize = 15.sp, fontWeight = FontWeight(800),
            color = if (line.checked) c.outline else c.primary, textDecoration = deco,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line.name, fontSize = 15.sp, lineHeight = 20.sp, color = if (line.checked) c.outline else c.onSurface, textDecoration = deco)
            if (line.recipes.isNotEmpty()) {
                Text(line.recipes.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
            }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(KIcons.MoreVert, contentDescription = "Optionen für ${line.name}", tint = c.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(text = { Text("Habe ich vorrätig") }, leadingIcon = { Icon(KIcons.Pantry, null) }, onClick = { menu = false; actions.onHaveAtHome(line) })
                if (line.manualId != null) {
                    DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(KIcons.Delete, null) }, onClick = { menu = false; actions.onRemove(line) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.pantryTab(state: ShoppingUiState, actions: ShoppingActions) {
    item(key = "padd") { AddField("Vorrat hinzufügen, z. B. Reis", actions.onAddPantry) }
    item(key = "pinfo") {
        Text(
            "Was hier steht, kommt nicht auf die Einkaufsliste. Gekauftes landet automatisch im Vorrat und wird beim Kochen wieder verbraucht.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        )
    }
    if (state.pantry.isEmpty()) item(key = "pempty") { EmptyHint("Der Vorrat ist leer.") }
    else item(key = "pchips") {
        ChipFlow(state.pantry.map { it.name }, actions.onRemovePantry, "aus dem Vorrat entfernen")
    }
    item(key = "staples") {
        Column(Modifier.padding(top = 24.dp)) {
            Text("Grundzutaten", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            Text(
                "Hast du immer im Haus – sie erscheinen nie auf der Einkaufsliste.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 8.dp),
            )
            ChipFlow(state.staples, actions.onRemoveStaple, "aus den Grundzutaten entfernen")
            Spacer(Modifier.height(8.dp))
            AddField("Grundzutat hinzufügen", actions.onAddStaple)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(names: List<String>, onRemove: (String) -> Unit, removeLabel: String) {
    val c = MaterialTheme.colorScheme
    FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        names.forEach { name ->
            InputChip(
                selected = false,
                onClick = { onRemove(name) },
                label = { Text(name) },
                trailingIcon = { Icon(KIcons.Close, contentDescription = "$name $removeLabel", modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = InputChipDefaults.inputChipColors(containerColor = c.secondaryContainer, labelColor = c.onSecondaryContainer, trailingIconColor = c.onSecondaryContainer),
                border = null,
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

@Composable
private fun AddField(placeholder: String, onAdd: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier.height(56.dp).background(c.surfaceContainerHighest, RoundedCornerShape(28.dp)).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, maxLines = 1)
                    inner()
                }
                FilledTonalIconButton(
                    onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.primary, contentColor = c.onPrimary),
                ) { Icon(KIcons.Add, contentDescription = "Hinzufügen") }
            }
        },
    )
}

@Composable
private fun EmptyHint(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(KIcons.Cart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { (label, onClick) ->
            Button(onClick = onClick) { Icon(KIcons.Calendar, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, fontFamily = RobotoFlexWide) }
        }
    }
}
