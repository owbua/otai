package io.github.owbua.otai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.Step
import io.github.owbua.otai.ui.overview.FILTER_ALL
import io.github.owbua.otai.ui.overview.OverviewActions
import io.github.owbua.otai.ui.overview.OverviewContent
import io.github.owbua.otai.ui.overview.buildOverview
import io.github.owbua.otai.ui.theme.OtaiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Kopf der Übersicht: verschwindet beim Runterscrollen, kommt beim Hochscrollen sofort wieder. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class QuickReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerReturnsOnScrollUpBeforeReachingTop() {
        val recipes = (1..40).map { Recipe(id = "r$it", title = "Rezept $it", steps = listOf(Step("x"))) }
        compose.setContent { OtaiTheme { OverviewContent(buildOverview(recipes, "", FILTER_ALL), { null }, OverviewActions()) } }
        val search = compose.onNodeWithText("Rezepte, Zutaten, Tags …")
        search.assertIsDisplayed()

        repeat(3) { compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top + bottom * 0.1f) } }
        compose.waitForIdle()
        search.assertIsNotDisplayed()
        compose.onNodeWithText("Rezept 1").assertIsNotDisplayed() // Liste ist nicht mehr oben

        // ein kurzes Stück hoch – Kopf ist wieder da, obwohl die Liste noch weit unten steht
        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = centerY + 300f) }
        compose.waitForIdle()
        search.assertIsDisplayed()
        compose.onNodeWithText("Rezept 1").assertIsNotDisplayed()
    }
}
