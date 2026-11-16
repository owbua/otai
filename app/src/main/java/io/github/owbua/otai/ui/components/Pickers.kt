package io.github.owbua.otai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.domain.dayLabel
import io.github.owbua.otai.domain.formatMinutes
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Kleine Rezeptzeile (Bild + Titel + Zeit/Kategorie) für Auswahllisten und den Wochenplan. */
@Composable
fun RecipeRowContent(recipe: Recipe, photo: File?, subtitle: String? = null, size: Int = 56) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RecipeArt(
            id = recipe.id, title = recipe.title, photo = photo,
            shape = RoundedCornerShape((size / 3.5f).dp),
            modifier = Modifier.size(size.dp),
            initialSize = (size * 0.42f).sp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val meta = subtitle ?: listOfNotNull(formatMinutes(recipe.times.totalMin ?: recipe.times.activeMin), recipe.category).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerSheet(
    title: String,
    recipes: List<Recipe>,
    photoFile: (String?) -> File?,
    onDismiss: () -> Unit,
    onPick: (Recipe) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase(Locale.GERMAN)
    val list = recipes.filter { r ->
        q.isEmpty() || (r.title + " " + r.category.orEmpty() + " " + r.tags.joinToString(" ") + " " + r.ingredients.joinToString(" ") { it.name })
            .lowercase(Locale.GERMAN).contains(q)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(KIcons.Search, null) },
                placeholder = { Text("Rezepte durchsuchen") },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.id }) { r ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onPick(r) }.padding(8.dp),
                    ) { RecipeRowContent(r, photoFile(r.photo)) }
                }
                if (list.isEmpty()) item {
                    Text("Kein Rezept gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** Tag (heute + 13 Tage) und Portionen wählen – für „Zum Wochenplan“ in der Rezeptansicht. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerSheet(
    today: LocalDate,
    initialServings: Int,
    plannedTitles: (LocalDate) -> List<String>,
    onDismiss: () -> Unit,
    onPick: (LocalDate, Int) -> Unit,
) {
    var servings by remember { mutableIntStateOf(initialServings) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Einplanen für …", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ServingsStepper(servings, { servings = (servings - 1).coerceAtLeast(1) }, { servings = (servings + 1).coerceAtMost(99) })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items((0L until 14L).map { today.plusDays(it) }) { d ->
                    val planned = plannedTitles(d)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onPick(d, servings) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(dayLabel(d, today), style = MaterialTheme.typography.titleSmall)
                        if (planned.isNotEmpty()) Text(planned.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ServingsDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var n by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Portionen") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ServingsStepper(n, { n = (n - 1).coerceAtLeast(1) }, { n = (n + 1).coerceAtMost(99) }) } },
        confirmButton = { TextButton(onClick = { onConfirm(n) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
