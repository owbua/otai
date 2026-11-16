package io.github.owbua.otai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.owbua.otai.data.model.Note
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.Step
import io.github.owbua.otai.data.model.Times
import io.github.owbua.otai.ui.cook.CookActions
import io.github.owbua.otai.ui.cook.CookContent
import io.github.owbua.otai.ui.cook.CookUiState
import io.github.owbua.otai.ui.cook.StepTimerState
import io.github.owbua.otai.ui.feedback.AmountField
import io.github.owbua.otai.ui.feedback.FeedbackActions
import io.github.owbua.otai.ui.feedback.FeedbackContent
import io.github.owbua.otai.ui.feedback.FeedbackState
import io.github.owbua.otai.ui.feedback.editableNumber
import io.github.owbua.otai.ui.overview.FILTER_ALL
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

/** Rendert die Screens ohne Emulator nach app/screenshots/ – zum Abgleich mit dem Design. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.Before fun decodeOnMainThread() {
        io.github.owbua.otai.ui.components.PhotoLoading.dispatcher = kotlinx.coroutines.Dispatchers.Main
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { OtaiTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val extra = listOf(
        Recipe(id = "okonomiyaki", title = "Okonomiyaki", category = "Japanisch", tags = listOf("Japanisch"), times = Times(35), favorite = true, steps = listOf(Step("x"))),
        Recipe(id = "miso-suppe", title = "Miso-Suppe mit Tofu", category = "Japanisch", tags = listOf("Japanisch", "Schnell"), times = Times(15), steps = listOf(Step("x"))),
        Recipe(id = "kaesespaetzle", title = "Käsespätzle", category = "Hauptgericht", tags = listOf("Vegetarisch"), times = Times(45), favorite = true, steps = listOf(Step("x"))),
        Recipe(id = "linsen-dal", title = "Linsen-Dal", category = "Hauptgericht", tags = listOf("Vegetarisch", "Schnell"), times = Times(30), steps = listOf(Step("x"))),
    )
    private val all get() = listOf(TestData.bauerntopf.copy(favorite = true), TestData.hackPilz) + extra

    @Test fun overview() = shot("1_uebersicht") { Overview() }
    @Test fun overviewDark() = shot("1_uebersicht_dunkel", dark = true) { Overview() }

    @Composable private fun Overview() = OverviewContent(
        state = buildOverview(all, "", FILTER_ALL),
        photoFile = { null }, actions = io.github.owbua.otai.ui.overview.OverviewActions(),
    )

    @Test fun overviewWithPhotos() = shot("1_uebersicht_fotos") {
        val photos = TestData.zipWithPhotos
        OverviewContent(
            state = buildOverview(photos.values.map { it.first } + extra, "", FILTER_ALL),
            photoFile = { name -> photos.values.firstOrNull { it.first.photo == name }?.second },
            actions = io.github.owbua.otai.ui.overview.OverviewActions(),
        )
    }

    @Test fun recipeWithPhoto() = shot("2_rezept_foto") {
        val (r, photo) = TestData.zipWithPhotos.getValue("hack-pilz-pfanne")
        RecipeContent(recipe = r, photo = photo, servings = 2, checked = emptySet(), actions = RecipeActions())
    }

    private val planState get(): io.github.owbua.otai.ui.plan.PlanUiState {
        val today = java.time.LocalDate.of(2026, 11, 18) // Mittwoch
        val photos = TestData.zipWithPhotos
        val recipes = photos.values.map { it.first } + extra
        val kitchen = io.github.owbua.otai.data.model.KitchenState(
            plan = listOf(
                io.github.owbua.otai.data.model.PlanEntry("a", "2026-11-17", "hack-pilz-pfanne", 2, cooked = true),
                io.github.owbua.otai.data.model.PlanEntry("b", "2026-11-18", "bauerntopf", 3),
                io.github.owbua.otai.data.model.PlanEntry("c", "2026-11-20", "okonomiyaki", 2, reason = "Einer deiner Favoriten"),
                io.github.owbua.otai.data.model.PlanEntry("d", "2026-11-23", "hack-pilz-pfanne", 4, reason = "Nutzt ebenfalls Kartoffel und Hackfleisch (wie Bauerntopf am Mi)"),
            ),
        )
        return io.github.owbua.otai.ui.plan.buildPlan(recipes, kitchen, today)
    }

    @Test fun plan() = shot("5_wochenplan") {
        val photos = TestData.zipWithPhotos
        io.github.owbua.otai.ui.plan.PlanContent(planState, { n -> photos.values.firstOrNull { it.first.photo == n }?.second }, io.github.owbua.otai.ui.plan.PlanActions())
    }

    @Test fun planDark() = shot("5_wochenplan_dunkel", dark = true) {
        val photos = TestData.zipWithPhotos
        io.github.owbua.otai.ui.plan.PlanContent(planState, { n -> photos.values.firstOrNull { it.first.photo == n }?.second }, io.github.owbua.otai.ui.plan.PlanActions())
    }

    private val shoppingState get(): io.github.owbua.otai.ui.shopping.ShoppingUiState {
        val today = java.time.LocalDate.of(2026, 11, 18)
        val kitchen = io.github.owbua.otai.data.model.KitchenState(
            plan = listOf(
                io.github.owbua.otai.data.model.PlanEntry("b", "2026-11-18", "bauerntopf", 3),
                io.github.owbua.otai.data.model.PlanEntry("d", "2026-11-23", "hack-pilz-pfanne", 4),
            ),
            manualItems = listOf(
                io.github.owbua.otai.data.model.ShoppingItem("m1", "Milch", 1.0, "l"),
                io.github.owbua.otai.data.model.ShoppingItem("m2", "Kaffee", checked = true),
            ),
            checkedPlanKeys = setOf("plan:hackfleisch|g"),
            pantry = listOf(io.github.owbua.otai.data.model.PantryItem("Tomatenmark"), io.github.owbua.otai.data.model.PantryItem("Reis"), io.github.owbua.otai.data.model.PantryItem("Zwiebeln")),
        )
        val recipes = listOf(TestData.bauerntopf, TestData.hackPilz)
        return io.github.owbua.otai.ui.shopping.ShoppingUiState(
            loading = false,
            lines = io.github.owbua.otai.domain.ShoppingPlanner.build(kitchen, recipes, today),
            pantry = kitchen.pantry, staples = kitchen.staples, plannedCount = 2,
        )
    }

    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    @Test fun shopping() = shot("6_einkauf") { io.github.owbua.otai.ui.shopping.ShoppingContent(shoppingState, io.github.owbua.otai.ui.shopping.ShoppingActions()) }

    @Test fun pantry() = shot("6_vorrat") { io.github.owbua.otai.ui.shopping.ShoppingContent(shoppingState, io.github.owbua.otai.ui.shopping.ShoppingActions(), initialTab = 1) }

    @Test fun packageSummary() {
        compose.setContent {
            OtaiTheme {
                io.github.owbua.otai.ui.PackageResultDialog(
                    io.github.owbua.otai.ui.PackageResult(
                        imported = 3, replaced = 1, photosAssigned = 2,
                        skipped = listOf(
                            io.github.owbua.otai.io.ImportProblem("paket.zip/fotos/katze.jpg", "Kein Rezept mit der ID „katze“ im Paket"),
                            io.github.owbua.otai.io.ImportProblem("paket.zip/fotos/notizen.txt", "Kein unterstütztes Bildformat (jpg, jpeg, png, webp)"),
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/7_paket_zusammenfassung.png")
    }

    @Test fun overviewSelection() = shot("1_uebersicht_auswahl") {
        val photos = TestData.zipWithPhotos
        val base = buildOverview(photos.values.map { it.first } + extra, "", FILTER_ALL)
        OverviewContent(
            state = base.copy(selected = setOf("bauerntopf", "kaesespaetzle")),
            photoFile = { name -> photos.values.firstOrNull { it.first.photo == name }?.second },
            actions = io.github.owbua.otai.ui.overview.OverviewActions(),
        )
    }

    @Test fun prompt() = shot("8_ki_prompt") {
        val md = listOf(java.io.File("../rezept-format/claude-prompt.md"), java.io.File("rezept-format/claude-prompt.md")).first { it.isFile }.readText()
        io.github.owbua.otai.ui.prompt.PromptScreen(io.github.owbua.otai.domain.KiPrompt.parse(md), onBack = {})
    }

    @Test fun newRecipeEditor() {
        val repo = io.github.owbua.otai.data.RecipeRepository(
            io.github.owbua.otai.data.RecipeStore(kotlin.io.path.createTempDirectory("r").toFile()),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val vm = io.github.owbua.otai.ui.edit.EditViewModel(repo, null)
        compose.setContent {
            OtaiTheme {
                val state by vm.state.collectAsState()
                if (state.loaded) io.github.owbua.otai.ui.edit.EditContent(state, vm, onClose = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/9_neues_rezept.png")
    }

    @Test fun photoViewer() {
        val photo = TestData.zipWithPhotos.getValue("bauerntopf").second
        compose.setContent { OtaiTheme { io.github.owbua.otai.ui.components.PhotoViewer(photo, "Bauerntopf", onDismiss = {}) } }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/10_foto_vollbild.png")
    }

    @Test fun overviewPantry() = shot("1_uebersicht_vorrat") {
        val kitchen = io.github.owbua.otai.data.model.KitchenState(pantry = listOf("Kartoffeln", "Möhren", "Paprikaschoten", "Hackfleisch", "Tomaten", "Tomatenmark", "Gemüsebrühe").map { io.github.owbua.otai.data.model.PantryItem(it) })
        OverviewContent(buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, kitchen), { null }, io.github.owbua.otai.ui.overview.OverviewActions())
    }

    @Test fun overviewPantryEmpty() = shot("1_uebersicht_vorrat_leer") {
        OverviewContent(buildOverview(all, "", io.github.owbua.otai.ui.overview.FILTER_PANTRY, io.github.owbua.otai.data.model.KitchenState()), { null }, io.github.owbua.otai.ui.overview.OverviewActions())
    }

    private val surpriseHits = listOf(
        io.github.owbua.otai.ui.surprise.HitUi(
            url = "https://www.chefkoch.de/rezepte/1/a.html", title = "Kartoffel-Hackfleisch-Pfanne mit Zwiebeln", imageUrl = null,
            meta = "35 Min · Hauptgericht", missing = emptyList(),
            ingredients = listOf(io.github.owbua.otai.ui.surprise.HitIngredient("500 g Kartoffeln", false), io.github.owbua.otai.ui.surprise.HitIngredient("300 g Hackfleisch", false)),
        ),
        io.github.owbua.otai.ui.surprise.HitUi(
            url = "https://www.chefkoch.de/rezepte/2/b.html", title = "Überbackener Hack-Kartoffel-Auflauf", imageUrl = null,
            meta = "1 Std · Auflauf", missing = listOf("Schlagsahne"),
            ingredients = listOf(
                io.github.owbua.otai.ui.surprise.HitIngredient("600 g Kartoffeln", false),
                io.github.owbua.otai.ui.surprise.HitIngredient("400 g Hackfleisch", false),
                io.github.owbua.otai.ui.surprise.HitIngredient("200 ml Schlagsahne", true),
                io.github.owbua.otai.ui.surprise.HitIngredient("1 Zwiebel", false),
            ),
        ),
        io.github.owbua.otai.ui.surprise.HitUi(
            url = "https://www.chefkoch.de/rezepte/3/c.html", title = "Bauerntopf", imageUrl = null,
            meta = "1 Std · Eintopf", missing = listOf("Paprikaschote", "Tomatenmark"), ingredients = emptyList(),
            existingId = "bauerntopf", onList = true,
        ),
    )

    @Test fun surprise() = shot("11_ueberrasch_mich") {
        io.github.owbua.otai.ui.surprise.SurpriseContent(
            io.github.owbua.otai.ui.surprise.SurpriseState(phase = io.github.owbua.otai.ui.surprise.SurprisePhase.Done, hits = surpriseHits, terms = listOf("Hackfleisch", "Kartoffeln"), checked = 12, maxMissing = 2),
            io.github.owbua.otai.ui.surprise.SurpriseActions(),
        )
    }

    @Test fun surpriseLoading() = shot("11_ueberrasch_mich_laden") {
        io.github.owbua.otai.ui.surprise.SurpriseContent(
            io.github.owbua.otai.ui.surprise.SurpriseState(
                phase = io.github.owbua.otai.ui.surprise.SurprisePhase.Loading(io.github.owbua.otai.domain.SurpriseProgress(listOf("Hackfleisch", "Kartoffeln"), 4, 12)),
                terms = listOf("Hackfleisch", "Kartoffeln"), maxMissing = 1,
            ),
            io.github.owbua.otai.ui.surprise.SurpriseActions(),
        )
    }

    @Test fun settings() = shot("12_einstellungen") {
        io.github.owbua.otai.ui.settings.SettingsContent(io.github.owbua.otai.data.Settings(surpriseMaxMissing = 1), io.github.owbua.otai.ui.settings.SettingsActions(), version = "1.5.0")
    }

    @Config(qualifiers = "w412dp-h1700dp-xxhdpi")
    @Test fun settingsFull() = shot("12_einstellungen_komplett") {
        io.github.owbua.otai.ui.settings.SettingsContent(io.github.owbua.otai.data.Settings(surpriseMaxMissing = 1), io.github.owbua.otai.ui.settings.SettingsActions(), version = "1.5.0")
    }

    @Test fun surpriseAny() = shot("11_ueberrasch_mich_beliebig") {
        io.github.owbua.otai.ui.surprise.SurpriseContent(
            io.github.owbua.otai.ui.surprise.SurpriseState(phase = io.github.owbua.otai.ui.surprise.SurprisePhase.Done, hits = surpriseHits.take(2), terms = listOf("Curry"), checked = 5, usePantry = false, minResults = 5),
            io.github.owbua.otai.ui.surprise.SurpriseActions(),
        )
    }

    @Test fun overviewEmpty() = shot("1_uebersicht_leer") {
        OverviewContent(buildOverview(emptyList(), "", FILTER_ALL), { null }, io.github.owbua.otai.ui.overview.OverviewActions())
    }

    private val cookedRecipe get() = TestData.bauerntopf.copy(
        favorite = true, cookedCount = 2, lastCooked = "2026-11-14", rating = 4,
        notes = listOf(Note("2026-11-14", "Mit Rinderhack gemacht, sehr gut.", listOf("Möhre(n): 2 → 3"), 4)),
    )

    @Test fun recipe() = shot("2_rezept") { RecipeView() }
    @Test fun recipeDark() = shot("2_rezept_dunkel", dark = true) { RecipeView() }

    @Config(qualifiers = "w412dp-h3600dp-xxhdpi")
    @Test fun recipeFull() = shot("2_rezept_komplett") { RecipeView() }

    @Composable private fun RecipeView() = RecipeContent(
        recipe = cookedRecipe, photo = null, servings = 3, checked = setOf("kartoffeln", "oel"), actions = RecipeActions(),
    )

    private val cookState get() = CookUiState(
        recipe = TestData.hackPilz, servings = 2, step = 2,
        timers = mapOf(2 to StepTimerState(180_000, 125_000), 0 to StepTimerState(1_200_000, 0, endAt = 1_000_000)),
        now = 0,
    )

    @Test fun cook() = shot("3_kochen") { CookContent(cookState, CookActions()) }
    @Test fun cookDark() = shot("3_kochen_dunkel", dark = true) { CookContent(cookState, CookActions()) }
    @Test fun cookFirstStep() = shot("3_kochen_schritt1") { CookContent(cookState.copy(step = 0, timers = emptyMap()), CookActions()) }

    private val feedbackState get(): FeedbackState {
        val r = TestData.bauerntopf
        return FeedbackState(
            recipe = r, servings = 3, rating = 4, note = "Etwas mehr Paprika nehmen.",
            amounts = r.ingredients.map {
                val init = editableNumber(it.amount)
                AmountField(it.key!!, it.name, it.unit, it.note, init, if (it.key == "moehren") "3" else init)
            },
        )
    }

    @Test fun feedback() = shot("4_rueckmeldung") { FeedbackContent(feedbackState, FeedbackActions()) }

    @Config(qualifiers = "w412dp-h2000dp-xxhdpi")
    @Test fun feedbackFull() = shot("4_rueckmeldung_komplett") { FeedbackContent(feedbackState, FeedbackActions()) }
}
