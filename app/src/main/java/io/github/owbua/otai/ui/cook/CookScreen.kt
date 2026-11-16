package io.github.owbua.otai.ui.cook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.domain.amountText
import io.github.owbua.otai.domain.formatTimer
import io.github.owbua.otai.domain.servingsLabel
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.components.ShapeBadge
import io.github.owbua.otai.ui.components.leadingShape
import io.github.owbua.otai.ui.components.pressedRadius
import io.github.owbua.otai.ui.components.trailingShape
import io.github.owbua.otai.ui.theme.CookStepTextStyle
import io.github.owbua.otai.ui.theme.RobotoFlexWide
import io.github.owbua.otai.ui.theme.RobotoFlexWider
import io.github.owbua.otai.ui.theme.TimerTextStyle

class CookActions(
    val onClose: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onToggleTimer: (Int) -> Unit = {},
    val onResetTimer: (Int) -> Unit = {},
    val onJumpTo: (Int) -> Unit = {},
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun CookContent(state: CookUiState, actions: CookActions) {
    val recipe = state.recipe ?: return
    val c = MaterialTheme.colorScheme
    val n = recipe.steps.size.coerceAtLeast(1)
    val i = state.step
    val step = recipe.steps.getOrNull(i)
    val factor = state.servings.toDouble() / recipe.servings
    var showIngredients by remember { mutableStateOf(false) }
    val stepLabel = "Schritt ${i + 1} von $n"

    Column(Modifier.fillMaxSize().background(c.surface)) {
        // Kopf
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = actions.onClose, modifier = Modifier.size(48.dp)) {
                Icon(KIcons.Close, contentDescription = "Kochansicht schließen", tint = c.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("$stepLabel · ${servingsLabel(state.servings)}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            FilledTonalIconButton(
                onClick = { showIngredients = true },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surfaceContainerHigh, contentColor = c.onSurfaceVariant),
            ) { Icon(KIcons.List, contentDescription = "Alle Zutaten anzeigen") }
        }

        // Fortschritt (wellig, M3 Expressive)
        val progress by animateFloatAsState((i + 1f) / n, MaterialTheme.motionScheme.slowSpatialSpec(), label = "progress")
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp).height(16.dp)
                .semantics { contentDescription = stepLabel },
            color = c.primary,
            trackColor = c.surfaceContainerHighest,
        )

        // Inhalt
        AnimatedContent(
            targetState = i,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { idx ->
            val s = recipe.steps.getOrNull(idx)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ShapeBadge(
                    text = "${idx + 1}",
                    size = 88.dp,
                    polygon = MaterialShapes.Sunny,
                    color = c.primary,
                    contentColor = c.onPrimary,
                    rotation = idx * 22f,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 40.sp, fontFamily = RobotoFlexWider),
                )
                val len = s?.text?.length ?: 0
                val textStyle = when {
                    len > 260 -> CookStepTextStyle.copy(fontSize = 20.sp, lineHeight = 28.sp)
                    len > 140 -> CookStepTextStyle.copy(fontSize = 23.sp, lineHeight = 32.sp)
                    else -> CookStepTextStyle
                }
                Text(s?.text.orEmpty(), style = textStyle, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })

                val ings = s?.ingredients.orEmpty().mapNotNull { key -> recipe.ingredients.find { it.key == key } }
                if (ings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Für diesen Schritt", style = MaterialTheme.typography.titleSmall, color = c.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ings.forEach { ing ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.secondaryContainer).height(40.dp).padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight(800))) { append(ing.amountText(factor)) }
                                            append("  ")
                                            append(ing.name)
                                        },
                                        color = c.onSecondaryContainer, fontSize = 15.sp, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                s?.timer?.let { def ->
                    val t = state.timers[idx]
                    val remaining = t?.remainingAt(state.now) ?: def.seconds * 1000L
                    TimerCard(
                        label = def.label ?: "Timer",
                        remainingMs = remaining,
                        running = t?.running == true,
                        finished = t != null && !t.running && t.remainingMs <= 0,
                        onToggle = { actions.onToggleTimer(idx) },
                        onReset = { actions.onResetTimer(idx) },
                    )
                }

                OtherTimers(state, idx, actions.onJumpTo)
            }
        }

        // Fuß
        Column(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(KIcons.Lightbulb, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Display bleibt eingeschaltet", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val prevSrc = remember { MutableInteractionSource() }
                Button(
                    onClick = actions.onPrev,
                    enabled = i > 0,
                    interactionSource = prevSrc,
                    shape = leadingShape(pressedRadius(prevSrc, 36.dp, 20.dp), 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
                    modifier = Modifier.weight(1f).height(72.dp),
                ) {
                    Icon(KIcons.ChevronLeft, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zurück", fontSize = 18.sp, fontWeight = FontWeight(700))
                }
                val nextSrc = remember { MutableInteractionSource() }
                val last = i >= n - 1
                Button(
                    onClick = if (last) actions.onFinish else actions.onNext,
                    interactionSource = nextSrc,
                    shape = trailingShape(pressedRadius(nextSrc, 36.dp, 20.dp), 12.dp),
                    modifier = Modifier.weight(1.6f).height(72.dp),
                ) {
                    if (last) {
                        Icon(KIcons.Check, null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fertig", fontSize = 20.sp, fontWeight = FontWeight(800), fontFamily = RobotoFlexWide)
                    } else {
                        Text("Weiter", fontSize = 20.sp, fontWeight = FontWeight(800), fontFamily = RobotoFlexWide)
                        Spacer(Modifier.width(8.dp))
                        Icon(KIcons.ChevronRight, null, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    if (showIngredients) IngredientsSheet(recipe, state.servings, factor) { showIngredients = false }
}

@Composable
private fun TimerCard(label: String, remainingMs: Long, running: Boolean, finished: Boolean, onToggle: () -> Unit, onReset: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (finished) c.primaryContainer else c.tertiaryContainer, label = "timerBg")
    val fg = if (finished) c.onPrimaryContainer else c.onTertiaryContainer
    val playRadius by animateDpAsState(if (running) 22.dp else 36.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "play")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(bg).padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (finished) "$label – fertig!" else label, style = MaterialTheme.typography.titleSmall, color = fg)
            Text(formatTimer(((remainingMs + 999) / 1000).toInt()), style = TimerTextStyle, color = fg, maxLines = 1)
        }
        FilledIconButton(
            onClick = onReset,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface, contentColor = fg),
        ) { Icon(KIcons.Replay, contentDescription = "Timer zurücksetzen", modifier = Modifier.size(22.dp)) }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(playRadius),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primary, contentColor = c.onPrimary),
        ) {
            Icon(
                if (running) KIcons.Pause else KIcons.PlayArrow,
                contentDescription = if (running) "Timer pausieren" else "Timer starten",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Timer anderer Schritte, die gerade laufen oder abgelaufen sind – antippen springt hin. */
@Composable
private fun OtherTimers(state: CookUiState, current: Int, onJump: (Int) -> Unit) {
    val recipe = state.recipe ?: return
    val others = state.timers.filter { (idx, t) -> idx != current && (t.running || t.remainingMs <= 0) }
    if (others.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        others.toSortedMap().forEach { (idx, t) ->
            val label = recipe.steps.getOrNull(idx)?.timer?.label ?: "Timer"
            val rem = t.remainingAt(state.now)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh)
                    .clickable { onJump(idx) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(KIcons.Timer, null, tint = c.primary, modifier = Modifier.size(20.dp))
                Text("Schritt ${idx + 1}: $label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (rem <= 0) "fertig" else formatTimer(((rem + 999) / 1000).toInt()), style = MaterialTheme.typography.titleSmall, color = c.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientsSheet(recipe: Recipe, servings: Int, factor: Double, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text("Zutaten · ${servingsLabel(servings)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            recipe.ingredients.forEach { ing ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(ing.amountText(factor), modifier = Modifier.width(88.dp), fontWeight = FontWeight(800), color = c.primary, fontSize = 15.sp)
                    Column(Modifier.weight(1f)) {
                        Text(ing.name, fontSize = 15.sp)
                        ing.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
