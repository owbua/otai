package io.github.owbua.otai.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/**
 * Gemeinsame Elemente zwischen Übersicht und Rezeptansicht: Die Kachel eines Rezepts fliegt beim
 * Öffnen in das Titelbild und beim Schließen (auch während der Zurück-Geste) an ihren Platz im Raster
 * zurück. Ohne Scope (Vorschau, Screenshot-Tests) sind die Modifier wirkungslos.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Der AnimatedContent-Scope des aktuellen Navigationsziels. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

object KMotion {
    /** M3 „emphasized decelerate“: für hereinkommende Inhalte */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 „emphasized accelerate“: für Inhalte, die gehen */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Straffe Feder mit einem Hauch Nachschwingen: Die Kachel landet zusammen mit der Überblendung
     * der Screens, statt hinterherzukriechen.
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ArtBounds = BoundsTransform { _, _ ->
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
    }
}

fun recipeArtKey(id: String) = "recipe-art-$id"

/**
 * Rezeptkachel bzw. Titelbild als gemeinsames Element.
 * Mit Foto ist der Inhalt auf beiden Seiten gleich → `sharedElement` (nur das Ziel wird gezeichnet,
 * kein Durchscheinen). Ohne Foto unterscheiden sich Kachel und Titelbild → `sharedBounds` blendet über.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRecipeArt(id: String, hasPhoto: Boolean): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        val state = rememberSharedContentState(recipeArtKey(id))
        if (hasPhoto) {
            this@sharedRecipeArt.sharedElement(state, scope, boundsTransform = KMotion.ArtBounds)
        } else {
            // Kachel und Titelbild haben dieselbe Grundfarbe; nur die Formen darauf blenden über.
            // Die alte Seite bleibt deckend stehen (ExitTransition.None), die neue blendet darüber ein → kein Durchscheinen.
            this@sharedRecipeArt.sharedBounds(
                state, scope,
                enter = fadeIn(tween(220, easing = KMotion.EmphasizedDecelerate)),
                exit = ExitTransition.None,
                boundsTransform = KMotion.ArtBounds,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    }
}

/**
 * Die Initiale fliegt als eigenes Element mit und wird dabei skaliert statt neu gesetzt – so wächst
 * das „B“ der Kachel ins „B“ des Titelbilds, statt doppelt zu erscheinen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRecipeLetter(id: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedRecipeLetter.sharedBounds(
            rememberSharedContentState("recipe-letter-$id"), scope,
            enter = fadeIn(tween(220, easing = KMotion.EmphasizedDecelerate)),
            exit = ExitTransition.None,
            boundsTransform = KMotion.ArtBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center),
            zIndexInOverlay = 1f,
        )
    }
}

/**
 * Knöpfe auf dem Titelbild: über dem fliegenden Bild zeichnen (sonst verschwänden sie darunter) und
 * erst einblenden, wenn es fast gelandet ist; beim Schließen sofort ausblenden.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroControls(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        with(scope) {
            this@heroControls
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2f)
                .animateEnterExit(
                    enter = fadeIn(tween(180, delayMillis = 220, easing = KMotion.EmphasizedDecelerate)),
                    exit = fadeOut(tween(80, easing = KMotion.EmphasizedAccelerate)),
                )
        }
    }
}
