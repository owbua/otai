package io.github.owbua.otai.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.owbua.otai.data.model.Note
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.SectionTitle
import io.github.owbua.otai.ui.components.ServingsStepper
import io.github.owbua.otai.ui.recipe.formatDate

/**
 * Stabile Callback-Sammlung: einmal pro ViewModel erzeugt, damit Zeilen-Composables bei
 * Änderungen anderer Zeilen übersprungen werden können (flüssiges Tippen/Scrollen).
 */
@Stable
class EditCallbacks(private val vm: EditViewModel) {
    val updateIngredient: (String, (EditIngredient) -> EditIngredient) -> Unit = vm::updateIngredient
    val removeIngredient: (String) -> Unit = vm::removeIngredient
    val updateStep: (String, (EditStep) -> EditStep) -> Unit = vm::updateStep
    val removeStep: (String) -> Unit = vm::removeStep
    val removeNote: (Int) -> Unit = vm::removeNote
}

private val FieldShape = RoundedCornerShape(16.dp)
private val NumberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val DecimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val SentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
fun EditContent(state: EditState, vm: EditViewModel, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val cb = remember(vm) { EditCallbacks(vm) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (vm.isDirty()) confirmDiscard = true else onClose() }
    BackHandler { close() }

    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = close, modifier = Modifier.size(48.dp)) { Icon(KIcons.Close, contentDescription = "Abbrechen") }
            Text(
                if (state.isNew) "Neues Rezept" else "Rezept bearbeiten",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Button(onClick = { vm.save() }, modifier = Modifier.height(48.dp)) { Text(if (state.isNew) "Anlegen" else "Speichern") }
        }
        state.error?.let {
            Text(it, color = c.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 28.dp, end = 16.dp, bottom = 8.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "basics", contentType = "basics") {
                BasicsEditor(
                    EditBasics(state.title, state.description, state.category, state.difficulty, state.tags, state.totalMin, state.activeMin, state.cookMin, state.error != null && !state.valid),
                    vm,
                )
            }
            item(key = "ing_header", contentType = "header") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle("Zutaten")
                        ServingsStepper(state.servings, { vm.update { it.copy(servings = (it.servings - 1).coerceAtLeast(1)) } }, { vm.update { it.copy(servings = (it.servings + 1).coerceAtMost(99)) } })
                    }
                    Text("Mengen gelten für die eingestellte Portionenzahl. Menge leer = nach Belieben.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            itemsIndexed(state.ingredients, key = { _, it -> it.uid }, contentType = { _, _ -> "ingredient" }) { _, ing ->
                IngredientEditor(ing, cb)
            }
            item(key = "ing_add", contentType = "add") {
                OutlinedButton(onClick = vm::addIngredient) {
                    Icon(KIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Zutat hinzufügen")
                }
            }
            item(key = "step_header", contentType = "header") { SectionTitle("Zubereitung", Modifier.padding(top = 16.dp)) }
            itemsIndexed(state.steps, key = { _, it -> it.uid }, contentType = { _, _ -> "step" }) { i, step ->
                StepEditor(i, step, cb)
            }
            item(key = "step_add", contentType = "add") {
                OutlinedButton(onClick = vm::addStep) {
                    Icon(KIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Schritt hinzufügen")
                }
            }
            if (state.notes.isNotEmpty()) {
                item(key = "notes_header", contentType = "header") { SectionTitle("Notizen", Modifier.padding(top = 16.dp)) }
                itemsIndexed(state.notes, contentType = { _, _ -> "note" }) { i, note -> NoteEditor(i, note, cb) }
            }
            item(key = "bottom") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (state.isNew) "Rezept verwerfen?" else "Änderungen verwerfen?") },
            text = { Text("Deine Eingaben gehen verloren.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Weiter bearbeiten") } },
        )
    }
}

/** Nur die Felder des Kopfbereichs – gleich bleibende Werte → Kopfbereich wird übersprungen. */
@androidx.compose.runtime.Immutable
private data class EditBasics(
    val title: String, val description: String, val category: String, val difficulty: String, val tags: String,
    val totalMin: String, val activeMin: String, val cookMin: String, val titleError: Boolean,
)

@Composable
private fun BasicsEditor(state: EditBasics, vm: EditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(state.title, { v -> vm.update { it.copy(title = v) } }, label = { Text("Titel") }, singleLine = true, shape = FieldShape, keyboardOptions = SentenceKeys, modifier = Modifier.fillMaxWidth(), isError = state.titleError)
        OutlinedTextField(state.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Beschreibung") }, shape = FieldShape, keyboardOptions = SentenceKeys, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.category, { v -> vm.update { it.copy(category = v) } }, label = { Text("Kategorie") }, singleLine = true, shape = FieldShape, keyboardOptions = SentenceKeys, modifier = Modifier.weight(1f))
            OutlinedTextField(state.difficulty, { v -> vm.update { it.copy(difficulty = v) } }, label = { Text("Aufwand") }, singleLine = true, shape = FieldShape, keyboardOptions = SentenceKeys, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(state.tags, { v -> vm.update { it.copy(tags = v) } }, label = { Text("Tags (mit Komma getrennt)") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.totalMin, { v -> vm.update { it.copy(totalMin = v.filter(Char::isDigit)) } }, label = { Text("Gesamt") }, suffix = { Text("Min") }, singleLine = true, keyboardOptions = NumberKeys, shape = FieldShape, modifier = Modifier.weight(1f))
            OutlinedTextField(state.activeMin, { v -> vm.update { it.copy(activeMin = v.filter(Char::isDigit)) } }, label = { Text("Aktiv") }, suffix = { Text("Min") }, singleLine = true, keyboardOptions = NumberKeys, shape = FieldShape, modifier = Modifier.weight(1f))
            OutlinedTextField(state.cookMin, { v -> vm.update { it.copy(cookMin = v.filter(Char::isDigit)) } }, label = { Text("Kochen") }, suffix = { Text("Min") }, singleLine = true, keyboardOptions = NumberKeys, shape = FieldShape, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IngredientEditor(ing: EditIngredient, cb: EditCallbacks) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Zutat zuerst, darunter Menge und Einheit
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(ing.name, { v -> cb.updateIngredient(ing.uid) { it.copy(name = v) } }, label = { Text("Zutat") }, singleLine = true, shape = FieldShape, keyboardOptions = SentenceKeys, modifier = Modifier.weight(1f))
            IconButton(onClick = { cb.removeIngredient(ing.uid) }) { Icon(KIcons.Delete, contentDescription = "Zutat entfernen", tint = c.onSurfaceVariant) }
        }
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ing.amount, { v -> cb.updateIngredient(ing.uid) { it.copy(amount = v) } }, label = { Text("Menge") }, singleLine = true, keyboardOptions = DecimalKeys, shape = FieldShape, modifier = Modifier.weight(1f))
            OutlinedTextField(ing.unit, { v -> cb.updateIngredient(ing.uid) { it.copy(unit = v) } }, label = { Text("Einheit") }, singleLine = true, shape = FieldShape, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(end = 8.dp)) {
            OutlinedTextField(ing.note, { v -> cb.updateIngredient(ing.uid) { it.copy(note = v) } }, label = { Text("Hinweis (optional)") }, singleLine = true, shape = FieldShape, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepEditor(index: Int, step: EditStep, cb: EditCallbacks) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow).padding(start = 12.dp, top = 8.dp, bottom = 12.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Schritt ${index + 1}", style = MaterialTheme.typography.titleSmall, color = c.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = { cb.removeStep(step.uid) }) { Icon(KIcons.Delete, contentDescription = "Schritt ${index + 1} entfernen", tint = c.onSurfaceVariant) }
        }
        Row(Modifier.padding(end = 8.dp)) {
            OutlinedTextField(step.text, { v -> cb.updateStep(step.uid) { it.copy(text = v) } }, shape = FieldShape, keyboardOptions = SentenceKeys, placeholder = { Text("Was ist zu tun?") }, modifier = Modifier.weight(1f), minLines = 2)
        }
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(step.timerMin, { v -> cb.updateStep(step.uid) { it.copy(timerMin = v) } }, label = { Text("Timer") }, suffix = { Text("Min") }, singleLine = true, keyboardOptions = DecimalKeys, shape = FieldShape, modifier = Modifier.width(120.dp))
            OutlinedTextField(step.timerLabel.orEmpty(), { v -> cb.updateStep(step.uid) { it.copy(timerLabel = v.ifEmpty { null }) } }, label = { Text("Timer-Name") }, singleLine = true, shape = FieldShape, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NoteEditor(index: Int, note: Note, cb: EditCallbacks) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.secondaryContainer).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDate(note.date), style = MaterialTheme.typography.labelLarge, color = c.onSecondaryContainer)
            val body = (listOf(note.text) + note.changes).filter { it.isNotBlank() }.joinToString("\n")
            if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer)
        }
        IconButton(onClick = { cb.removeNote(index) }) { Icon(KIcons.Delete, contentDescription = "Notiz löschen", tint = c.onSecondaryContainer) }
    }
}
