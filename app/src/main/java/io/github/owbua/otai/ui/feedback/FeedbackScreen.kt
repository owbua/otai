package io.github.owbua.otai.ui.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.domain.servingsLabel
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.SectionTitle
import io.github.owbua.otai.ui.components.ShapeBadge
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.pressedRadius
import io.github.owbua.otai.ui.components.trailingShape
import io.github.owbua.otai.ui.theme.RobotoFlexWide

class FeedbackActions(
    val onRating: (Int) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onAmount: (String, String) -> Unit = { _, _ -> },
    val onNudge: (String, Int) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onSkip: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedbackContent(state: FeedbackState, actions: FeedbackActions) {
    val recipe = state.recipe ?: return
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 32.dp, bottom = 24.dp),
        ) {
            // Kopf
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    ShapeBadge(
                        text = "",
                        size = 88.dp,
                        polygon = MaterialShapes.SoftBurst,
                        color = c.tertiaryContainer,
                        rotation = 12f,
                    )
                    Icon(KIcons.Check, contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(40.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Guten Appetit!", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${recipe.title} · ${servingsLabel(state.servings)}",
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                    )
                }
            }

            // Bewertung
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                SectionTitle("Wie war's?", Modifier.padding(start = 8.dp, bottom = 8.dp))
                Row(Modifier.padding(start = 0.dp)) {
                    (1..5).forEach { n ->
                        val on = (state.rating ?: 0) >= n
                        val scale by animateFloatAsState(if (on) 1.15f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "star")
                        IconToggleButton(checked = on, onCheckedChange = { actions.onRating(n) }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                if (on) KIcons.Star else KIcons.StarBorder,
                                contentDescription = "$n von 5 Sternen",
                                tint = if (on) c.primary else c.outline,
                                modifier = Modifier.size(36.dp).scale(scale),
                            )
                        }
                    }
                }
            }

            // Mengen
            if (state.amounts.isNotEmpty()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
                    SectionTitle("Mengen anpassen", Modifier.padding(start = 8.dp))
                    Text(
                        "Angezeigt für ${servingsLabel(state.servings)}. Was du änderst, wird ins Rezept übernommen und als Notiz festgehalten.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                    )
                    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.amounts.forEach { f -> AmountRow(f, actions) }
                    }
                }
            }

            // Notiz
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                SectionTitle("Notiz", Modifier.padding(start = 8.dp, bottom = 12.dp))
                OutlinedTextField(
                    value = state.note,
                    onValueChange = actions.onNote,
                    placeholder = { Text("Was würdest du nächstes Mal anders machen?") },
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Fuß: verbundene Buttons
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val skipSrc = remember { MutableInteractionSource() }
            Button(
                onClick = actions.onSkip,
                interactionSource = skipSrc,
                shape = leadingShape(pressedRadius(skipSrc, 32.dp, 18.dp), 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Überspringen", fontSize = 16.sp, fontWeight = FontWeight(700)) }
            val saveSrc = remember { MutableInteractionSource() }
            Button(
                onClick = actions.onSave,
                interactionSource = saveSrc,
                shape = trailingShape(pressedRadius(saveSrc, 32.dp, 18.dp), 12.dp),
                modifier = Modifier.weight(1.4f).height(64.dp),
            ) {
                Icon(KIcons.Check, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Speichern", fontSize = 18.sp, fontWeight = FontWeight(800), fontFamily = RobotoFlexWide)
            }
        }
    }
}

@Composable
private fun AmountRow(f: AmountField, actions: FeedbackActions) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (f.changed) c.secondaryContainer else c.surfaceContainerLow)
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 15.sp, lineHeight = 20.sp, color = c.onSurface)
            if (f.changed && f.initial.isNotEmpty()) {
                Text("vorher ${f.initial}${f.unit?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            } else f.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val tone = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surfaceContainerHighest, contentColor = c.onSurface)
            FilledTonalIconButton(onClick = { actions.onNudge(f.key, -1) }, shape = leadingShape(20.dp, 6.dp), colors = tone, modifier = Modifier.size(44.dp)) {
                Icon(KIcons.Remove, contentDescription = "${f.name} weniger", modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.width(84.dp).height(44.dp).background(c.surfaceContainerHighest, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = f.text,
                    onValueChange = { actions.onAmount(f.key, it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = c.primary, fontWeight = FontWeight(800), textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Menge ${f.name}" },
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.weight(1f, fill = false)) {
                                if (f.text.isEmpty()) Text("n. B.", style = MaterialTheme.typography.titleSmall, color = c.outline)
                                inner()
                            }
                            f.unit?.let { Text(" $it", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, maxLines = 1) }
                        }
                    },
                )
            }
            FilledTonalIconButton(onClick = { actions.onNudge(f.key, +1) }, shape = trailingShape(20.dp, 6.dp), colors = tone, modifier = Modifier.size(44.dp)) {
                Icon(KIcons.Add, contentDescription = "${f.name} mehr", modifier = Modifier.size(18.dp))
            }
        }
    }
}
