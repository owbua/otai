package io.github.owbua.otai.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.github.owbua.otai.ui.theme.Otai
import io.github.owbua.otai.ui.theme.RobotoFlexWider
import io.github.owbua.otai.ui.theme.RobotoFlexWidest
import io.github.owbua.otai.ui.theme.TileTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Die fünf Formen aus dem Design (cookie, clover, sunny, flower, burst) samt Drehung. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TILE_SHAPES: List<Pair<RoundedPolygon, Float>> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided to 0f,
        MaterialShapes.Clover4Leaf to 20f,
        MaterialShapes.Sunny to 10f,
        MaterialShapes.Flower to 0f,
        MaterialShapes.Burst to 0f,
        MaterialShapes.Cookie9Sided to 15f,
    )
}

private fun stableHash(id: String) = abs(id.fold(7) { h, c -> h * 31 + c.code })

@Composable
fun toneFor(id: String): TileTone = Otai.colors.tones[stableHash(id) % 3]

fun shapeFor(id: String): Pair<RoundedPolygon, Float> = TILE_SHAPES[(stableHash(id) / 3) % TILE_SHAPES.size]

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundedPolygon.asShape(): Shape = toShape()

/**
 * Rezeptbild: Foto, falls vorhanden, sonst getönte Fläche mit MaterialShape und Initiale.
 */
@Composable
fun RecipeArt(
    id: String,
    title: String,
    photo: File?,
    shape: Shape,
    modifier: Modifier = Modifier,
    initialSize: TextUnit = MaterialTheme.typography.displayLarge.fontSize,
    shapeScale: Float = 0.82f,
    hero: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val tone = toneFor(id)
    val bitmap = rememberPhoto(photo, if (hero) 1600 else 700)
    Box(modifier.clip(shape).background(tone.bg), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val (poly, rot) = shapeFor(id)
            if (hero) {
                HeroShapes(tone)
            } else {
                Box(
                    Modifier
                        .fillMaxSize(shapeScale)
                        .wrapContentSize()
                        .aspectRatio(1f) // bleibt rund, auch wenn die Kachel beim Übergang breiter wird
                        .rotate(rot)
                        .clip(poly.asShape())
                        .background(tone.shape),
                )
            }
            Text(
                text = title.trim().take(1).uppercase(),
                color = tone.fg,
                fontSize = initialSize,
                fontFamily = if (hero) RobotoFlexWidest else RobotoFlexWider,
                fontWeight = FontWeight(if (hero) 900 else 800),
                modifier = (if (hero) Modifier.offset(x = (-30).dp) else Modifier).sharedRecipeLetter(id),
            )
        }
        overlay()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.HeroShapes(tone: TileTone) {
    // Großes Cookie links oben und kleines Kleeblatt rechts unten – wie im Design der Rezeptansicht
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset(x = (-36).dp, y = (-40).dp)
            .requiredSize(300.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(tone.shape),
    )
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 20.dp, y = 24.dp)
            .size(120.dp)
            .rotate(20f)
            .clip(MaterialShapes.Clover4Leaf.toShape())
            .background(Otai.colors.tones[1].bg),
    )
}

/** Kleines rundes Herz-Badge für Favoriten. */
@Composable
fun FavoriteBadge(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KIcons.Favorite, contentDescription = "Favorit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.56f))
    }
}

// --- Fotos laden -------------------------------------------------------------------------------

/** Thread fürs Dekodieren; in Screenshot-Tests (Robolectric) auf den Main-Thread umstellbar. */
object PhotoLoading {
    @Volatile var dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
}

private val PhotoSizes = listOf(700, 1600)

private object PhotoCache {
    val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

@Composable
fun rememberPhoto(file: File?, maxSizePx: Int): ImageBitmap? {
    val key = file?.let { "${it.path}@$maxSizePx" }
    val cached = key?.let { PhotoCache.cache.get(it) }
    // Bis die passende Auflösung da ist, eine schon geladene andere zeigen (z. B. die der Kachel im
    // Titelbild) – sonst flöge beim Öffnen erst ein Platzhalter statt des Fotos.
    val initial = cached ?: file?.let { f -> PhotoSizes.firstNotNullOfOrNull { PhotoCache.cache.get("${f.path}@$it") } }
    val state by produceState(initialValue = initial, key) {
        if (file == null || key == null) { value = null; return@produceState }
        if (cached != null) return@produceState
        value = withContext(PhotoLoading.dispatcher) {
            runCatching { decodeSampled(file, maxSizePx) }.getOrNull()
        }?.also { PhotoCache.cache.put(key, it) }
    }
    return state
}

private fun decodeSampled(file: File, maxSizePx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSizePx || bounds.outHeight / (sample * 2) >= maxSizePx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
}
