package io.github.owbua.otai

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.owbua.otai.ui.NavMotion
import io.github.owbua.otai.ui.OverviewRoute
import io.github.owbua.otai.ui.RecipeRoute
import io.github.owbua.otai.ui.components.LocalNavAnimatedScope
import io.github.owbua.otai.ui.components.LocalSharedTransitionScope
import io.github.owbua.otai.ui.overview.FILTER_ALL
import io.github.owbua.otai.ui.overview.OverviewActions
import io.github.owbua.otai.ui.overview.OverviewContent
import io.github.owbua.otai.ui.overview.buildOverview
import io.github.owbua.otai.ui.recipe.RecipeActions
import io.github.owbua.otai.ui.recipe.RecipeContent
import io.github.owbua.otai.ui.theme.OtaiTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Übergang Übersicht ↔ Rezept mit der Kachel als gemeinsamem Element. Hält die Zeit an und
 * speichert Einzelbilder nach build/transition-frames/ zur Sichtprüfung.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun decodeOnMainThread() {
        io.github.owbua.otai.ui.components.PhotoLoading.dispatcher = kotlinx.coroutines.Dispatchers.Main
    }

    private fun run(withPhotos: Boolean, prefix: String, gesture: Boolean = false) {
        val photos = TestData.zipWithPhotos
        val recipes = if (withPhotos) photos.values.map { it.first } else listOf(TestData.bauerntopf, TestData.hackPilz)
        val photoFor: (String?) -> java.io.File? = { name -> if (!withPhotos || name == null) null else photos.values.firstOrNull { it.first.photo == name }?.second }
        lateinit var nav: NavHostController
        compose.setContent {
            OtaiTheme {
                nav = rememberNavController()
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(
                            nav, startDestination = OverviewRoute,
                            enterTransition = NavMotion.enter, exitTransition = NavMotion.exit,
                            popEnterTransition = NavMotion.popEnter, popExitTransition = NavMotion.popExit,
                            predictivePopEnterTransition = NavMotion.predictivePopEnter, predictivePopExitTransition = NavMotion.predictivePopExit,
                        ) {
                            composable<OverviewRoute> {
                                CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                                    OverviewContent(
                                        state = buildOverview(recipes, "", FILTER_ALL),
                                        photoFile = photoFor,
                                        actions = OverviewActions(onOpen = { nav.navigate(RecipeRoute(it)) }),
                                    )
                                }
                            }
                            composable<RecipeRoute> { entry ->
                                val r = recipes.first { it.id == entry.toRoute<RecipeRoute>().id }
                                CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                                    RecipeContent(r, photoFor(r.photo), r.servings, emptySet(), RecipeActions())
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val title = recipes.first().title
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(title).performClick()
        frames("$prefix-oeffnen")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Kochen starten").assertExists()

        compose.mainClock.autoAdvance = false
        if (gesture) {
            // Vorhersage-Zurück-Geste vom linken Rand: Zwischenstände festhalten, dann loslassen
            val back = compose.activity.onBackPressedDispatcher
            compose.runOnUiThread { back.dispatchOnBackStarted(BackEventCompat(0f, 1200f, 0f, BackEventCompat.EDGE_LEFT)) }
            for (p in listOf(0.25f, 0.5f, 0.75f)) {
                compose.runOnUiThread { back.dispatchOnBackProgressed(BackEventCompat(p * 600f, 1200f, p, BackEventCompat.EDGE_LEFT)) }
                compose.mainClock.advanceTimeBy(32)
                compose.onRoot().captureRoboImage("build/transition-frames/$prefix-geste-${(p * 100).toInt()}.png")
            }
            compose.runOnUiThread { back.onBackPressed() }
            frames("$prefix-geste-los")
        } else {
            compose.runOnUiThread { nav.popBackStack() }
            frames("$prefix-schliessen")
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Kochen starten").assertDoesNotExist()
    }

    private fun frames(name: String) {
        var t = 0L
        for (at in listOf(16L, 80L, 160L, 240L, 340L, 600L)) {
            compose.mainClock.advanceTimeBy(at - t)
            t = at
            compose.onRoot().captureRoboImage("build/transition-frames/$name-${"%03d".format(at)}.png")
        }
    }

    @Test fun recipeTransitionWithPhoto() = run(withPhotos = true, prefix = "foto")
    @Test fun recipeTransitionWithoutPhoto() = run(withPhotos = false, prefix = "ohne")
    @Test fun recipePredictiveBack() = run(withPhotos = true, prefix = "foto", gesture = true)
}
