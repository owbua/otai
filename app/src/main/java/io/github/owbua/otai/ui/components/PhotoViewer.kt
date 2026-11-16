package io.github.owbua.otai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Vollbild-Fotoansicht: Pinch-Zoom (bis 5×), Verschieben, Doppeltipp zum Zoomen/Zurücksetzen.
 * Zurück-Geste oder ✕ schließt.
 */
@Composable
fun PhotoViewer(photo: File, title: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val bitmap = rememberPhoto(photo, 1600) // gleicher Cache wie das Titelbild → sofort da
        val scope = rememberCoroutineScope()
        val scale = remember { Animatable(1f) }
        val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            // Größe des eingepassten Bildes (ContentScale.Fit), damit man nicht ins Schwarze schieben kann
            val ar = bitmap?.let { it.width.toFloat() / it.height } ?: (boxW / boxH)
            val fitW = min(boxW, boxH * ar)
            val fitH = fitW / ar

            fun clamp(o: Offset, s: Float): Offset {
                val maxX = max(0f, (fitW * s - boxW) / 2f)
                val maxY = max(0f, (fitH * s - boxH) / 2f)
                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Foto: $title",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boxW, boxH, fitW) {
                            detectTapGestures(onDoubleTap = { tap ->
                                scope.launch {
                                    if (scale.value > 1.05f) {
                                        launch { scale.animateTo(1f) }
                                        offset.animateTo(Offset.Zero)
                                    } else {
                                        val center = Offset(boxW / 2f, boxH / 2f)
                                        val target = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                                        launch { scale.animateTo(DOUBLE_TAP_ZOOM) }
                                        offset.animateTo(target)
                                    }
                                }
                            })
                        }
                        .pointerInput(boxW, boxH, fitW) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val old = scale.value
                                val new = (old * zoom).coerceIn(1f, MAX_ZOOM)
                                // um den Fingermittelpunkt zoomen
                                val center = Offset(boxW / 2f, boxH / 2f)
                                val focus = centroid - center
                                // Punkt unter den Fingern bleibt fix: o' = o·(n/a) + f·(1 − n/a) + pan
                                val ratio = new / old
                                val next = offset.value * ratio + focus * (1f - ratio) + pan
                                scope.launch {
                                    scale.snapTo(new)
                                    offset.snapTo(clamp(next, new))
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationX = offset.value.x
                            translationY = offset.value.y
                        },
                )
            } else {
                Text("Foto wird geladen …", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }

            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
            ) { Icon(KIcons.Close, contentDescription = "Foto schließen") }
        }
    }
}
