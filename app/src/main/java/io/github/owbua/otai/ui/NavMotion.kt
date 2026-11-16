package io.github.owbua.otai.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import io.github.owbua.otai.ui.components.KMotion

/**
 * Übergänge zwischen den Screens, nach den M3-Bewegungsmustern:
 * - Übersicht ↔ Rezept: Die Kachel fliegt als gemeinsames Element, die Screens blenden nur über.
 *   Würden sie sich zusätzlich bewegen, liefe die Kachel einem wandernden Ziel hinterher.
 * - Zwischen den Tabs der Navigationsleiste: „Fade through“.
 * - Tiefer hinein: gemeinsame X-Achse (leicht nach links schieben, überblenden).
 * - Zurück: Der Screen schrumpft und verblasst – passend zur Vorhersage-Zurück-Geste, die diesen
 *   Übergang mit dem Finger steuert.
 */
internal object NavMotion {
    private const val ENTER = 400
    private const val EXIT = 200

    private fun NavDestination.isTop() =
        hasRoute(OverviewRoute::class) || hasRoute(PlanRoute::class) || hasRoute(ShoppingRoute::class)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharesArt(): Boolean {
        val from = initialState.destination
        val to = targetState.destination
        return (from.hasRoute(OverviewRoute::class) && to.hasRoute(RecipeRoute::class)) ||
            (from.hasRoute(RecipeRoute::class) && to.hasRoute(OverviewRoute::class))
    }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
        initialState.destination.isTop() && targetState.destination.isTop()

    private val fadeInShared = fadeIn(tween(ENTER, delayMillis = 50, easing = KMotion.EmphasizedDecelerate))
    private val fadeOutShared = fadeOut(tween(EXIT, easing = KMotion.EmphasizedAccelerate))

    private val fadeThroughIn =
        fadeIn(tween(220, delayMillis = 90, easing = KMotion.EmphasizedDecelerate)) +
            scaleIn(tween(220, delayMillis = 90, easing = KMotion.EmphasizedDecelerate), initialScale = 0.94f)
    private val fadeThroughOut = fadeOut(tween(90, easing = KMotion.EmphasizedAccelerate))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            sharesArt() -> fadeInShared
            betweenTabs() -> fadeThroughIn
            else -> fadeIn(tween(ENTER, easing = KMotion.EmphasizedDecelerate)) +
                slideInHorizontally(tween(ENTER + 100, easing = KMotion.EmphasizedDecelerate)) { it / 8 }
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            sharesArt() -> fadeOutShared
            betweenTabs() -> fadeThroughOut
            else -> fadeOut(tween(EXIT, easing = KMotion.EmphasizedAccelerate)) +
                slideOutHorizontally(tween(ENTER, easing = KMotion.EmphasizedAccelerate)) { -it / 12 }
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            sharesArt() -> fadeInShared
            betweenTabs() -> fadeThroughIn
            else -> fadeIn(tween(ENTER, easing = KMotion.EmphasizedDecelerate)) +
                scaleIn(tween(ENTER, easing = KMotion.EmphasizedDecelerate), initialScale = 1.04f)
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            sharesArt() -> fadeOutShared
            betweenTabs() -> fadeThroughOut
            else -> fadeOut(tween(250, easing = KMotion.EmphasizedAccelerate)) +
                scaleOut(tween(300, easing = KMotion.EmphasizedAccelerate), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 0.5f))
        }
    }

    /*
     * Die Zurück-Geste hat in Navigation Compose eigene Übergänge mit eigenem Standard (Screen schrumpft
     * auf 70 %, ohne auszublenden). Hier dieselben wie beim normalen Zurück, damit Geste und Pfeil gleich
     * aussehen und die Kachel mit dem Finger zurückfliegt.
     */
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { popEnter() }
    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { popExit() }
}
