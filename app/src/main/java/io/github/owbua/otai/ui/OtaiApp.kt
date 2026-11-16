package io.github.owbua.otai.ui

import android.Manifest
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.owbua.otai.ui.components.KMotion
import io.github.owbua.otai.ui.components.LocalNavAnimatedScope
import io.github.owbua.otai.ui.components.LocalSharedTransitionScope
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.owbua.otai.domain.dayLabel
import io.github.owbua.otai.ui.components.DayPickerSheet
import io.github.owbua.otai.ui.components.KIcons
import io.github.owbua.otai.ui.overview.OverviewActions
import io.github.owbua.otai.ui.prompt.PromptScreen
import io.github.owbua.otai.domain.SurpriseFinder
import io.github.owbua.otai.io.web.WebFetcher
import io.github.owbua.otai.util.WebViewHtml
import io.github.owbua.otai.ui.settings.SettingsActions
import io.github.owbua.otai.ui.settings.SettingsContent
import io.github.owbua.otai.ui.surprise.SurpriseActions
import io.github.owbua.otai.ui.surprise.SurpriseContent
import io.github.owbua.otai.ui.surprise.SurpriseViewModel
import io.github.owbua.otai.ui.surprise.WebImages
import io.github.owbua.otai.ui.prompt.rememberKiPrompt
import io.github.owbua.otai.ui.plan.PlanActions
import io.github.owbua.otai.ui.plan.PlanContent
import io.github.owbua.otai.ui.plan.PlanViewModel
import io.github.owbua.otai.ui.shopping.ShoppingActions
import io.github.owbua.otai.ui.shopping.ShoppingContent
import io.github.owbua.otai.ui.shopping.ShoppingViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.owbua.otai.OtaiApplication
import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.ui.cook.CookActions
import io.github.owbua.otai.ui.cook.CookContent
import io.github.owbua.otai.ui.cook.CookViewModel
import io.github.owbua.otai.ui.edit.EditContent
import io.github.owbua.otai.ui.edit.EditViewModel
import io.github.owbua.otai.ui.feedback.FeedbackActions
import io.github.owbua.otai.ui.feedback.FeedbackContent
import io.github.owbua.otai.ui.feedback.FeedbackViewModel
import io.github.owbua.otai.ui.overview.OverviewContent
import io.github.owbua.otai.ui.overview.OverviewViewModel
import io.github.owbua.otai.ui.recipe.RecipeActions
import io.github.owbua.otai.ui.recipe.RecipeContent
import io.github.owbua.otai.ui.recipe.RecipeViewModel
import io.github.owbua.otai.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable object OverviewRoute
@Serializable data class RecipeRoute(val id: String)
@Serializable data class CookRoute(val id: String, val servings: Int)
@Serializable data class FeedbackRoute(val id: String, val servings: Int)
/** id = null → neues Rezept anlegen */
@Serializable data class EditRoute(val id: String? = null)
@Serializable object PromptRoute
@Serializable object PlanRoute
@Serializable object ShoppingRoute
@Serializable object SurpriseRoute
@Serializable object SettingsRoute

private data class TopLevel(val route: Any, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OtaiApp(io: ImportExportViewModel) {
    val app = LocalContext.current.applicationContext as OtaiApplication
    val repo = app.repository
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(io) { io.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(io) { io.shareIntents.collect { runCatching { context.startActivity(it) } } }
    LaunchedEffect(io) { io.openRecipe.collect { id -> nav.navigate(RecipeRoute(id)) { launchSingleTop = true } } }

    val ioState by io.state.collectAsStateWithLifecycle()

    val kitchen = app.kitchen
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val tops = remember {
        listOf(
            TopLevel(OverviewRoute, "Rezepte", KIcons.Book),
            TopLevel(PlanRoute, "Wochenplan", KIcons.Calendar),
            TopLevel(ShoppingRoute, "Einkauf", KIcons.Cart),
        )
    }
    val density = LocalDensity.current
    // Höhe der Navigationsleiste (gemessen); Schätzung bis zur ersten Messung
    var barHeight by remember { mutableStateOf(64.dp) }
    val showBar = dest?.let { d -> d.hasRoute(OverviewRoute::class) || d.hasRoute(PlanRoute::class) || d.hasRoute(ShoppingRoute::class) } ?: true

    androidx.compose.material3.Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Leiste gleitet weg bzw. herein, statt beim Öffnen eines Rezepts schlagartig zu verschwinden
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(350, easing = KMotion.EmphasizedDecelerate)) { it },
                exit = slideOutVertically(tween(200, easing = KMotion.EmphasizedAccelerate)) { it },
            ) {
                ShortNavigationBar(
                    modifier = Modifier.onSizeChanged { if (it.height > 0) barHeight = with(density) { it.height.toDp() } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    tops.forEach { t ->
                        val selected = dest?.hasRoute(t.route::class) == true
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { _ ->
      // Die Screens liegen unter der Leiste und reservieren ihren Platz selbst (TopLevelScreen), damit
      // sich beim Ein-/Ausblenden der Leiste nichts verschiebt.
      SharedTransitionLayout {
       CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = nav,
            startDestination = OverviewRoute,
            enterTransition = NavMotion.enter,
            exitTransition = NavMotion.exit,
            popEnterTransition = NavMotion.popEnter,
            popExitTransition = NavMotion.popExit,
            predictivePopEnterTransition = NavMotion.predictivePopEnter,
            predictivePopExitTransition = NavMotion.predictivePopExit,
        ) {
            composable<OverviewRoute> {
              TopLevelScreen(barHeight, this) {
                val vm = viewModel { OverviewViewModel(repo, kitchen, app.settings.state.map { it.pantryFilterMaxMissing }) }
                val state by vm.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var exportIds by remember { mutableStateOf<List<String>>(emptyList()) }
                val packageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    if (uri != null) { io.exportPackage(exportIds, uri); vm.clearSelection() }
                }
                val actions = remember(vm) {
                    OverviewActions(
                        onQuery = vm::setQuery,
                        onFilter = vm::setFilter,
                        onOpen = { nav.navigate(RecipeRoute(it)) },
                        onNew = { nav.navigate(EditRoute(null)) },
                        onSurprise = { nav.navigate(SurpriseRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                        onToggleSelect = vm::toggleSelection,
                        onSelectAll = vm::selectAllVisible,
                        onClearSelection = vm::clearSelection,
                        onFavoriteSelected = { vm.toggleFavoriteSelected() },
                        onExportSelected = {
                            exportIds = vm.selectedIds()
                            scope.launch { packageLauncher.launch(io.packageFileName(exportIds)) }
                        },
                        onShareSelected = { io.sharePackage(vm.selectedIds()) },
                        onDeleteSelected = {
                            val n = vm.selectedIds().size
                            vm.deleteSelected()
                            io.message(if (n == 1) "1 Rezept gelöscht" else "$n Rezepte gelöscht")
                        },
                    )
                }
                OverviewContent(state = state, photoFile = repo::photoPath, actions = actions, busy = ioState.busy)
              }
            }

            composable<PlanRoute> {
              TopLevelScreen(barHeight, this) {
                val vm = viewModel { PlanViewModel(repo, kitchen) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.refreshToday() }
                PlanContent(
                    state = state,
                    photoFile = repo::photoPath,
                    actions = PlanActions(
                        onOpenRecipe = { nav.navigate(RecipeRoute(it)) },
                        onPick = vm::pick,
                        onSwap = vm::swap,
                        onSuggest = vm::suggest,
                        onSuggestAnother = vm::suggestAnother,
                        onRemove = vm::remove,
                        onServings = vm::setServings,
                        onCooked = vm::setCooked,
                        onOpenShopping = { nav.navigate(ShoppingRoute) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    ),
                )
              }
            }

            composable<ShoppingRoute> {
              TopLevelScreen(barHeight, this) {
                val vm = viewModel { ShoppingViewModel(repo, kitchen) }
                val state by vm.state.collectAsStateWithLifecycle()
                ShoppingContent(
                    state = state,
                    actions = ShoppingActions(
                        onAdd = { vm.add(it) },
                        onToggle = { vm.toggle(it) },
                        onRemove = { vm.remove(it) },
                        onHaveAtHome = { vm.haveAtHome(it) },
                        onCheckout = { vm.checkoutToPantry() },
                        onShare = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, vm.shareText())
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Einkaufsliste")
                            }
                            runCatching { context.startActivity(android.content.Intent.createChooser(send, "Einkaufsliste teilen")) }
                        },
                        onAddPantry = { vm.addPantry(it) },
                        onRemovePantry = { vm.removePantry(it) },
                        onAddStaple = { vm.addStaple(it) },
                        onRemoveStaple = { vm.removeStaple(it) },
                        onOpenPlan = { nav.navigate(PlanRoute) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    ),
                )
              }
            }

            composable<RecipeRoute> { entry ->
              CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                val id = entry.toRoute<RecipeRoute>().id
                val vm = viewModel { RecipeViewModel(repo, id, kitchen) }
                var planSheet by remember { mutableStateOf(false) }
                val kitchenState by kitchen.state.collectAsStateWithLifecycle()
                val state by vm.state.collectAsStateWithLifecycle()
                val deleted by vm.deleted.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) scope.launch {
                        val bytes = withContext(Dispatchers.IO) { Photos.loadAsJpeg(context, uri) }
                        if (bytes != null) vm.setPhoto(bytes) else io.message("Foto konnte nicht geladen werden")
                    }
                }
                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    uri?.let { io.exportRecipe(id, it) }
                }
                LaunchedEffect(deleted) { if (deleted) nav.popBackStack() }
                LaunchedEffect(state.loading, state.recipe) {
                    if (!state.loading && state.recipe == null && !deleted) nav.popBackStack()
                }
                val recipe = state.recipe
                if (recipe != null) {
                    RecipeContent(
                        recipe = recipe,
                        photo = repo.photoPath(recipe.photo),
                        servings = state.servings,
                        checked = state.checked,
                        actions = RecipeActions(
                            onBack = { nav.popBackStack() },
                            onToggleFavorite = { vm.toggleFavorite() },
                            onEdit = { nav.navigate(EditRoute(id)) },
                            onLess = { vm.less() },
                            onMore = { vm.more() },
                            onToggleIngredient = { vm.toggleIngredient(it) },
                            onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRemovePhoto = { vm.removePhoto() },
                            onPhotoFromSource = { io.message("Foto wird geladen …"); io.loadPhotoFromSource(id) },
                            onExport = { exportLauncher.launch(io.recipeFileName(id, recipe.title)) },
                            onShare = { io.shareRecipe(id) },
                            onDelete = { vm.delete() },
                            onCook = { nav.navigate(CookRoute(id, state.servings)) },
                            onAddToPlan = { planSheet = true },
                        ),
                    )
                    if (planSheet) {
                        val today = remember { java.time.LocalDate.now() }
                        val titles = remember(kitchenState) {
                            val all = repo.recipes.value.orEmpty().associateBy { it.id }
                            kitchenState?.plan.orEmpty().groupBy { it.date }.mapValues { (_, es) -> es.mapNotNull { all[it.recipeId]?.title } }
                        }
                        DayPickerSheet(
                            today = today,
                            initialServings = state.servings,
                            plannedTitles = { titles[it.toString()].orEmpty() },
                            onDismiss = { planSheet = false },
                            onPick = { date, servings ->
                                planSheet = false
                                vm.addToPlan(date, servings)
                                io.message("„${recipe.title}“ für ${dayLabel(date, today)} eingeplant")
                            },
                        )
                    }
                }
              }
            }

            composable<CookRoute> { entry ->
                val route = entry.toRoute<CookRoute>()
                val vm = viewModel {
                    CookViewModel(
                        repo, route.id, route.servings, createSavedStateHandle(),
                        onTimerFinished = { title, label -> app.timerAlerts.timerFinished(title, label) },
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                KeepScreenOn()
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                var askedPermission by remember { mutableStateOf(false) }
                CookContent(
                    state = state,
                    actions = CookActions(
                        onClose = { nav.popBackStack() },
                        onPrev = vm::prev,
                        onNext = vm::next,
                        onFinish = {
                            nav.navigate(FeedbackRoute(route.id, route.servings)) {
                                popUpTo<CookRoute> { inclusive = true }
                            }
                        },
                        onToggleTimer = {
                            if (Build.VERSION.SDK_INT >= 33 && !askedPermission) {
                                askedPermission = true
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.toggleTimer(it)
                        },
                        onResetTimer = vm::resetTimer,
                        onJumpTo = vm::setStep,
                    ),
                )
            }

            composable<FeedbackRoute> { entry ->
                val route = entry.toRoute<FeedbackRoute>()
                val vm = viewModel { FeedbackViewModel(repo, route.id, route.servings, kitchen) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.saved) { if (state.saved) nav.popBackStack() }
                FeedbackContent(
                    state = state,
                    actions = FeedbackActions(
                        onRating = vm::setRating,
                        onNote = vm::setNote,
                        onAmount = vm::setAmount,
                        onNudge = vm::nudge,
                        onSave = { vm.save() },
                        onSkip = { vm.skip() },
                    ),
                )
            }

            composable<EditRoute> { entry ->
                val id = entry.toRoute<EditRoute>().id
                val vm = viewModel { EditViewModel(repo, id) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.savedId) {
                    val saved = state.savedId ?: return@LaunchedEffect
                    if (id == null) {
                        // neues Rezept: direkt die Rezeptansicht öffnen (dort z. B. Foto hinzufügen)
                        nav.navigate(RecipeRoute(saved)) { popUpTo<EditRoute> { inclusive = true } }
                        io.message("Rezept angelegt")
                    } else nav.popBackStack()
                }
                if (state.loaded) EditContent(state, vm, onClose = { nav.popBackStack() })
            }

            composable<SurpriseRoute> {
                val vm = viewModel {
                    SurpriseViewModel(
                        SurpriseFinder(
                            loadDirect = { url -> withContext(Dispatchers.IO) { WebFetcher.fetchHtml(url) } },
                            loadBrowser = { url -> WebViewHtml.load(app, url) },
                        ),
                        kitchen, repo, app.settings,
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                SurpriseContent(
                    state = state,
                    actions = remember(vm) {
                        SurpriseActions(
                            onBack = { nav.popBackStack() },
                            onRoll = vm::roll,
                            onUsePantry = vm::setUsePantry,
                            onImport = { url -> vm.web(url)?.let { io.importWebRecipe(it, url, WebImages.jpeg(it.imageUrl)) } },
                            onOpen = { id -> nav.navigate(RecipeRoute(id)) },
                            onAddMissing = { url ->
                                vm.addMissing(url)
                                io.message("Fehlende Zutaten stehen auf der Einkaufsliste")
                            },
                            onSettings = { nav.navigate(SettingsRoute) },
                        )
                    },
                )
            }

            composable<SettingsRoute> {
                val settings by app.settings.state.collectAsStateWithLifecycle()
                val recipes by repo.recipes.collectAsStateWithLifecycle()
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    io.importUris(uris)
                }
                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    uri?.let(io::exportAll)
                }
                var urlDialog by remember { mutableStateOf(false) }
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                SettingsContent(
                    settings = settings,
                    version = version,
                    exportEnabled = !recipes.isNullOrEmpty(),
                    actions = remember {
                        SettingsActions(
                            onBack = { nav.popBackStack() },
                            onMaxMissing = app.settings::setSurpriseMaxMissing,
                            onMinResults = app.settings::setSurpriseMinResults,
                            onPantryFilterMaxMissing = app.settings::setPantryFilterMaxMissing,
                            onImportFile = { importLauncher.launch(arrayOf("application/json", "application/zip", "application/x-zip-compressed", "text/plain", "application/octet-stream", "*/*")) },
                            onImportUrl = { urlDialog = true },
                            onPrompt = { nav.navigate(PromptRoute) },
                            onExportAll = { exportLauncher.launch(io.backupFileName()) },
                        )
                    },
                )
                if (urlDialog) UrlImportDialog(onDismiss = { urlDialog = false }, onImport = { urlDialog = false; io.importFromUrl(it) })
            }

            composable<PromptRoute> {
                PromptScreen(rememberKiPrompt(), onBack = { nav.popBackStack() }, onCopied = { io.message(it) })
            }
        }
       }
      }

    }

    ioState.pending?.let { pending ->
        val n = pending.conflicts.size
        AlertDialog(
            onDismissRequest = { io.resolveConflicts(null) },
            title = { Text(if (n == 1) "Rezept existiert bereits" else "$n Rezepte existieren bereits") },
            text = {
                Text(
                    pending.conflicts.joinToString("\n") { "• ${it.recipe.title}" } +
                        "\n\nErsetzen überschreibt das vorhandene Rezept samt Notizen. „Als Kopie“ legt ein zweites an.",
                )
            },
            confirmButton = { TextButton(onClick = { io.resolveConflicts(ConflictMode.REPLACE) }) { Text("Ersetzen") } },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { io.resolveConflicts(ConflictMode.COPY) }) { Text("Als Kopie") }
                    TextButton(onClick = { io.resolveConflicts(ConflictMode.SKIP) }) { Text("Überspringen") }
                }
            },
        )
    }

    ioState.packageResult?.let { result -> PackageResultDialog(result, onDismiss = io::dismissPackageResult) }

    if (ioState.pending == null && ioState.problems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = io::dismissProblems,
            title = { Text("Nicht importiert") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ioState.problems.forEach {
                        Text(it.sourceName, style = MaterialTheme.typography.titleSmall)
                        Text(it.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = io::dismissProblems) { Text("OK") } },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Zusammenfassung nach einem Rezept-Paket: importiert, Fotos zugeordnet, übersprungen (mit Grund). */
@Composable
fun PackageResultDialog(result: PackageResult, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    fun n(count: Int, one: String, many: String) = if (count == 1) "1 $one" else "$count $many"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(KIcons.Import, null) },
        title = { Text("Rezept-Paket importiert") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    n(result.imported, "Rezept importiert", "Rezepte importiert") +
                        (if (result.replaced > 0) " (davon ${result.replaced} ersetzt)" else "") + "\n" +
                        n(result.photosAssigned, "Foto zugeordnet", "Fotos zugeordnet") + "\n" +
                        "${result.skipped.size} übersprungen",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (result.skipped.isNotEmpty()) {
                    Text("Übersprungen", style = MaterialTheme.typography.titleSmall, color = c.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    result.skipped.forEach {
                        Text(it.sourceName.substringAfter('/'), style = MaterialTheme.typography.titleSmall)
                        Text(it.message, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    val initial = remember {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        val clip = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        io.github.owbua.otai.io.web.WebFetcher.findUrl(clip).orEmpty()
    }
    var url by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(io.github.owbua.otai.ui.components.KIcons.Link, null) },
        title = { Text("Von Webseite importieren") },
        text = {
            Column {
                Text(
                    "Link zu einem Rezept, z. B. von Chefkoch. Foto, Zutaten und Schritte werden übernommen. " +
                        "Tipp: Im Browser auch über Teilen → otai möglich.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://www.chefkoch.de/rezepte/…") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(url) }, enabled = url.isNotBlank()) { Text("Importieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Tab-Screen: lässt unten Platz für die Navigationsleiste, die darüber liegt. */
@Composable
private fun TopLevelScreen(barHeight: Dp, scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val bar = PaddingValues(bottom = barHeight)
    CompositionLocalProvider(LocalNavAnimatedScope provides scope) {
        Box(Modifier.fillMaxSize().padding(bar).consumeWindowInsets(bar)) { content() }
    }
}
