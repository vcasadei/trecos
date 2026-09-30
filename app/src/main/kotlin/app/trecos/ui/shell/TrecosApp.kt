package app.trecos.ui.shell

import android.app.Activity
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.trecos.data.StartScreen
import app.trecos.ui.settings.SettingsScreen
import app.trecos.ui.backup.BackupScreen
import app.trecos.ui.settings.ProfileScreen
import app.trecos.ui.settings.SettingsNavigation
import app.trecos.ui.settings.HouseFieldsScreen
import app.trecos.ui.settings.ExtrasScreen
import app.trecos.ui.settings.CurrencyScreen
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.trecos.R
import app.trecos.ui.appContainer
import app.trecos.ui.appViewModel
import app.trecos.ui.places.BottomBarClearance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.trecos.ui.places.ContainerFormScreen
import app.trecos.ui.places.HomeScreen
import app.trecos.ui.places.HouseFormScreen
import app.trecos.ui.places.ItemFormScreen
import app.trecos.ui.places.ItemScreen
import app.trecos.ui.places.PlaceNavigation
import app.trecos.ui.places.PlaceScreen
import app.trecos.ui.places.TagsScreen
import app.trecos.ui.search.SearchScreen
import app.trecos.ui.places.TrashScreen
import app.trecos.ui.places.KeepScreen
import app.trecos.ui.places.DeleteHouseScreen
import app.trecos.ui.theme.LocalDarkTheme

/** Test tag of the house band behind the status bar. */
const val HOUSE_BAND_TAG = "house_band"

/**
 * Returns the navigation route of a tab's root screen.
 *
 * @param tab the tab.
 * @return the route, such as `search`.
 */
fun rootRoute(tab: TrecosTab): String = tab.name.lowercase()

/**
 * Returns the test tag of a tab's root screen.
 *
 * @param tab the tab.
 * @return the tag, such as `screen_search`.
 */
fun rootScreenTag(tab: TrecosTab): String = "screen_${rootRoute(tab)}"

/** Routes of the place screens and forms. */
private object Routes {
    const val PLACE = "place/{house}?container={container}"
    const val ITEM = "item/{item}"
    const val TAGS = "tags/{house}"
    const val TRASH = "trash/{house}"
    const val KEEP = "keep/{container}"
    const val DELETE_HOUSE = "house/delete/{house}"
    const val CURRENCY = "settings/currency"
    const val EXTRAS = "settings/extras"
    const val FIELDS = "settings/fields/{house}"
    const val PROFILE = "settings/profile"
    const val BACKUP = "settings/backup"
    const val HOUSE_FORM = "form/house?id={id}"
    const val CONTAINER_FORM = "form/container?house={house}&parent={parent}&id={id}&qr={qr}"
    const val ITEM_FORM = "form/item?house={house}&container={container}&id={id}&qr={qr}"

    /** Whether a route is a form or a task screen with its own bottom buttons, which hide the bottom bar. */
    fun isForm(route: String?) = route != null && (route.startsWith("form/") || route == KEEP || route == DELETE_HOUSE)
}

/**
 * The app shell: the navigation graph, the house band and the bottom bar.
 * Home is the start destination, so system back on the Search or Settings
 * root returns to Home, and back on the Home root leaves the app.
 *
 * @param navController the controller; created and remembered by default.
 */
@Composable
fun TrecosApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val currentTab = TrecosTab.entries.firstOrNull { rootRoute(it) == route } ?: TrecosTab.Home
    val shell = appViewModel { ShellViewModel(it) }
    val band by shell.band.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = band == null && !dark
        }
    }
    val nav = remember(navController) { placeNavigation(navController) }
    val app = appContainer()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    var startApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(app) {
        if (!startApplied) {
            startApplied = true
            if (app.preferences.startScreen.first() == StartScreen.Search) navController.navigateToTab(TrecosTab.Search)
        }
    }
    LaunchedEffect(app) {
        app.messages.collect { message ->
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = message.text,
                    actionLabel = if (message.undoEntries.isNotEmpty()) undoLabel else null,
                    duration = if (message.undoEntries.isNotEmpty()) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.undoEntries.forEach { app.organize.restore(it) }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController, startDestination = rootRoute(TrecosTab.Home), modifier = Modifier.fillMaxSize()) {
                composable(rootRoute(TrecosTab.Search)) {
                    Box(Modifier.fillMaxSize().testTag(rootScreenTag(TrecosTab.Search))) { SearchScreen(nav) }
                }
                composable(rootRoute(TrecosTab.Settings)) {
                    Box(Modifier.fillMaxSize().testTag(rootScreenTag(TrecosTab.Settings))) {
                        SettingsScreen(
                            SettingsNavigation(
                                openCurrency = { navController.navigate(Routes.CURRENCY) },
                                openExtras = { navController.navigate(Routes.EXTRAS) },
                                openFields = { house -> navController.navigate("settings/fields/$house") },
                                openTags = nav.openTags,
                                openTrash = nav.openTrash,
                                openProfile = { navController.navigate(Routes.PROFILE) },
                                openBackup = { navController.navigate(Routes.BACKUP) },
                            ),
                        )
                    }
                }
                composable(Routes.CURRENCY) { CurrencyScreen { navController.popBackStack() } }
                composable(Routes.BACKUP) { BackupScreen { navController.popBackStack() } }
                composable(Routes.PROFILE) { ProfileScreen { navController.popBackStack() } }
                composable(Routes.EXTRAS) { ExtrasScreen { navController.popBackStack() } }
                composable(Routes.FIELDS, listOf(stringArg("house"))) { entry -> HouseFieldsScreen(entry.string("house")!!) { navController.popBackStack() } }
                composable(rootRoute(TrecosTab.Home)) {
                    Box(Modifier.fillMaxSize().testTag(rootScreenTag(TrecosTab.Home))) { HomeScreen(nav) }
                }
                composable(Routes.PLACE, listOf(stringArg("house"), optionalArg("container"))) { entry ->
                    PlaceScreen(entry.string("house")!!, entry.string("container"), nav, isTabRoot = false)
                }
                composable(Routes.ITEM, listOf(stringArg("item"))) { entry -> ItemScreen(entry.string("item")!!, nav) }
                composable(Routes.TAGS, listOf(stringArg("house"))) { entry -> TagsScreen(entry.string("house")!!) { navController.popBackStack() } }
                composable(Routes.TRASH, listOf(stringArg("house"))) { entry -> TrashScreen(entry.string("house")!!) { navController.popBackStack() } }
                composable(Routes.KEEP, listOf(stringArg("container"))) { entry ->
                    KeepScreen(entry.string("container")!!, onBack = { navController.popBackStack() }, onFinished = { navController.popBackStack() })
                }
                composable(Routes.DELETE_HOUSE, listOf(stringArg("house"))) { entry ->
                    DeleteHouseScreen(
                        entry.string("house")!!,
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack(rootRoute(TrecosTab.Home), inclusive = false) },
                    )
                }
                composable(Routes.HOUSE_FORM, listOf(optionalArg("id"))) { entry ->
                    HouseFormScreen(entry.string("id")) { savedId ->
                        navController.popBackStack()
                        if (savedId != null && entry.string("id") == null) nav.openHouse(savedId)
                    }
                }
                composable(Routes.CONTAINER_FORM, listOf(optionalArg("house"), optionalArg("parent"), optionalArg("id"), optionalArg("qr"))) { entry ->
                    ContainerFormScreen(
                        entry.string("house").orEmpty(), entry.string("parent"), entry.string("id"),
                        onDone = { navController.popBackStack() },
                        onOpenHolder = { holder -> openHolder(navController, holder) },
                        scannedCode = entry.string("qr"),
                    )
                }
                composable(Routes.ITEM_FORM, listOf(optionalArg("house"), optionalArg("container"), optionalArg("id"), optionalArg("qr"))) { entry ->
                    ItemFormScreen(
                        entry.string("house").orEmpty(), entry.string("container"), entry.string("id"),
                        onDone = { navController.popBackStack() },
                        onOpenHolder = { holder -> openHolder(navController, holder) },
                        scannedCode = entry.string("qr"),
                    )
                }
            }
            band?.let { colour ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(colour.band)
                        .testTag(HOUSE_BAND_TAG),
                )
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (Routes.isForm(route)) 96.dp else BottomBarClearance),
            )
            if (!Routes.isForm(route)) {
                TrecosBottomBar(
                    selected = currentTab,
                    onSelect = { tab -> navController.navigateToTab(tab) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Builds the place screens' navigation actions on top of the controller.
 *
 * @param controller the navigation controller.
 * @return the actions.
 */
private fun placeNavigation(controller: NavHostController) = PlaceNavigation(
    back = { controller.popBackStack() },
    openHouse = { _ -> controller.popBackStack(rootRoute(TrecosTab.Home), inclusive = false) },
    openContainer = { house, container -> controller.navigate("place/$house?container=$container") },
    openItem = { item -> controller.navigate("item/$item") },
    addHouse = { controller.navigate("form/house") },
    editHouse = { house -> controller.navigate("form/house?id=$house") },
    addContainer = { house, parent -> controller.navigate("form/container?house=$house" + (parent?.let { "&parent=$it" } ?: "")) },
    editContainer = { container -> controller.navigate("form/container?id=$container") },
    addItem = { house, container -> controller.navigate("form/item?house=$house" + (container?.let { "&container=$it" } ?: "")) },
    editItem = { item -> controller.navigate("form/item?id=$item") },
    openTags = { house -> controller.navigate("tags/$house") },
    openTrash = { house -> controller.navigate("trash/$house") },
    keep = { container -> controller.navigate("keep/$container") },
    deleteHouse = { house -> controller.navigate("house/delete/$house") },
    searchIn = { controller.navigateToTab(TrecosTab.Search) },
    addContainerWithCode = { house, parent, code ->
        controller.navigate("form/container?house=$house" + (parent?.let { "&parent=$it" } ?: "") + "&qr=${Uri.encode(code)}")
    },
    addItemWithCode = { house, container, code ->
        controller.navigate("form/item?house=$house" + (container?.let { "&container=$it" } ?: "") + "&qr=${Uri.encode(code)}")
    },
)

/**
 * Opens the item or container holding a QR code, leaving the form.
 *
 * @param controller the navigation controller.
 * @param holder the holder.
 */
private fun openHolder(controller: NavHostController, holder: app.trecos.data.QrHolder) {
    controller.popBackStack()
    if (holder.kind == "item") controller.navigate("item/${holder.id}") else controller.navigate("place/${holder.houseId}?container=${holder.id}")
}

/**
 * @param name the argument name.
 * @return a required string argument.
 */
private fun stringArg(name: String) = navArgument(name) { type = NavType.StringType }

/**
 * @param name the argument name.
 * @return an optional string argument that defaults to `null`.
 */
private fun optionalArg(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/**
 * @param name the argument name.
 * @return the argument's value, or `null`.
 */
private fun androidx.navigation.NavBackStackEntry.string(name: String): String? = arguments?.getString(name)

/**
 * Opens a tab's root, keeping one entry per tab above Home and restoring the
 * tab's previous state.
 *
 * @param tab the tab to open.
 */
private fun NavHostController.navigateToTab(tab: TrecosTab) {
    navigate(rootRoute(tab)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

