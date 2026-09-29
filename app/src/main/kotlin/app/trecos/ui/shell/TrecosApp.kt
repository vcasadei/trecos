package app.trecos.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

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

/**
 * The app shell: the navigation graph with the three tab roots, and the
 * bottom bar. Home is the start destination, so system back on the Search or
 * Settings root returns to Home, and back on the Home root leaves the app.
 *
 * @param navController the controller; created and remembered by default.
 */
@Composable
fun TrecosApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TrecosTab.entries.firstOrNull { rootRoute(it) == backStackEntry?.destination?.route }
        ?: TrecosTab.Home
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController, startDestination = rootRoute(TrecosTab.Home), modifier = Modifier.fillMaxSize()) {
                TrecosTab.entries.forEach { tab ->
                    composable(rootRoute(tab)) { TabRootScreen(tab) }
                }
            }
            TrecosBottomBar(
                selected = currentTab,
                onSelect = { tab -> navController.navigateToTab(tab) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

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
 * A tab root, which has a title and no back arrow. Its content arrives with
 * the releases that build each tab.
 *
 * @param tab the tab this root belongs to.
 */
@Composable
private fun TabRootScreen(tab: TrecosTab) {
    Column(Modifier.fillMaxSize().testTag(rootScreenTag(tab))) {
        TrecosTopBar(title = stringResource(tab.label), onBack = null)
    }
}
