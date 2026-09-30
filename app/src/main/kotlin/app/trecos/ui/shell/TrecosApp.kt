package app.trecos.ui.shell

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import app.trecos.ui.appViewModel
import app.trecos.ui.places.ContainerFormScreen
import app.trecos.ui.places.HomeScreen
import app.trecos.ui.places.HouseFormScreen
import app.trecos.ui.places.ItemFormScreen
import app.trecos.ui.places.ItemScreen
import app.trecos.ui.places.PlaceNavigation
import app.trecos.ui.places.PlaceScreen
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
    const val HOUSE_FORM = "form/house?id={id}"
    const val CONTAINER_FORM = "form/container?house={house}&parent={parent}&id={id}"
    const val ITEM_FORM = "form/item?house={house}&container={container}&id={id}"

    /** Whether a route is a form, which hides the bottom bar. */
    fun isForm(route: String?) = route?.startsWith("form/") == true
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController, startDestination = rootRoute(TrecosTab.Home), modifier = Modifier.fillMaxSize()) {
                composable(rootRoute(TrecosTab.Search)) { TabRootScreen(TrecosTab.Search) }
                composable(rootRoute(TrecosTab.Settings)) { TabRootScreen(TrecosTab.Settings) }
                composable(rootRoute(TrecosTab.Home)) {
                    Box(Modifier.fillMaxSize().testTag(rootScreenTag(TrecosTab.Home))) { HomeScreen(nav) }
                }
                composable(Routes.PLACE, listOf(stringArg("house"), optionalArg("container"))) { entry ->
                    PlaceScreen(entry.string("house")!!, entry.string("container"), nav, isTabRoot = false)
                }
                composable(Routes.ITEM, listOf(stringArg("item"))) { entry -> ItemScreen(entry.string("item")!!, nav) }
                composable(Routes.HOUSE_FORM, listOf(optionalArg("id"))) { entry ->
                    HouseFormScreen(entry.string("id")) { savedId ->
                        navController.popBackStack()
                        if (savedId != null && entry.string("id") == null) nav.openHouse(savedId)
                    }
                }
                composable(Routes.CONTAINER_FORM, listOf(optionalArg("house"), optionalArg("parent"), optionalArg("id"))) { entry ->
                    ContainerFormScreen(entry.string("house").orEmpty(), entry.string("parent"), entry.string("id")) { navController.popBackStack() }
                }
                composable(Routes.ITEM_FORM, listOf(optionalArg("house"), optionalArg("container"), optionalArg("id"))) { entry ->
                    ItemFormScreen(entry.string("house").orEmpty(), entry.string("container"), entry.string("id")) { navController.popBackStack() }
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
)

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

/**
 * A tab root that has no content yet (Search and Settings arrive in later
 * releases): a title and no back arrow.
 *
 * @param tab the tab this root belongs to.
 */
@Composable
private fun TabRootScreen(tab: TrecosTab) {
    Column(Modifier.fillMaxSize().testTag(rootScreenTag(tab))) {
        TrecosTopBar(title = stringResource(tab.label), onBack = null)
    }
}
