package io.github.owbua.otai.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import io.github.owbua.otai.domain.servingsLabel
import io.github.owbua.otai.ui.theme.RobotoFlexWide

/** Form-Paar für verbundene Buttons (M3 Expressive „connected button group“). */
fun leadingShape(outer: Dp, inner: Dp) = RoundedCornerShape(outer, inner, inner, outer)
fun trailingShape(outer: Dp, inner: Dp) = RoundedCornerShape(inner, outer, outer, inner)

/** Ecken-Morph beim Drücken, wie bei M3-Expressive-Buttons. */
@Composable
fun pressedRadius(interaction: MutableInteractionSource, normal: Dp, pressed: Dp): Dp {
    val isPressed by interaction.collectIsPressedAsState()
    val r by animateDpAsState(if (isPressed) pressed else normal, label = "pressRadius")
    return r
}

/** Nummer in einer MaterialShape (Cookie in der Rezeptansicht, Sunny in der Kochansicht). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBadge(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    rotation: Float = 0f,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(800)),
) {
    val rot by animateFloatAsState(rotation, MaterialTheme.motionScheme.slowSpatialSpec(), label = "badgeRotation")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(rot)
                .clip(polygon.toShape())
                .background(color),
        )
        Text(text, color = contentColor, style = style)
    }
}

/** Portionen-Stepper: − | n Portionen | + als verbundene Gruppe. */
@Composable
fun ServingsStepper(servings: Int, onLess: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.semantics { }, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        val lessSrc = remember { MutableInteractionSource() }
        val moreSrc = remember { MutableInteractionSource() }
        FilledTonalIconButton(
            onClick = onLess,
            enabled = servings > 1,
            interactionSource = lessSrc,
            shape = leadingShape(pressedRadius(lessSrc, 24.dp, 12.dp), 8.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
            modifier = Modifier.size(48.dp),
        ) { Icon(KIcons.Remove, contentDescription = "Weniger Portionen", modifier = Modifier.size(20.dp)) }
        Surface(
            color = c.secondaryContainer,
            contentColor = c.onSecondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(48.dp).widthIn(min = 92.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(servingsLabel(servings), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(700)))
            }
        }
        FilledTonalIconButton(
            onClick = onMore,
            enabled = servings < 99,
            interactionSource = moreSrc,
            shape = trailingShape(pressedRadius(moreSrc, 24.dp, 12.dp), 8.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
            modifier = Modifier.size(48.dp),
        ) { Icon(KIcons.Add, contentDescription = "Mehr Portionen", modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)
}

/** Kleiner, nicht klickbarer Tag (8 dp, Outline) wie in der Rezeptansicht. */
@Composable
fun TagLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.defaultMinSize(minHeight = 32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
        }
    }
}

/** Stat-Kachel „35 Min / Gesamt“. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 22.sp, lineHeight = 28.sp, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kleiner Info-Chip (z. B. Timer-Dauer in der Schrittliste). */
@Composable
fun InfoChip(icon: ImageVector, text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .height(28.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight(700)))
    }
}
