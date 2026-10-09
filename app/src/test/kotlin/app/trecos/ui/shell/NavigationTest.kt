package app.trecos.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.trecos.MainActivity
import app.trecos.ui.theme.TrecosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios of the app-shell "Back navigation" requirement.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationTest {

    @get:Rule
    val appRule = createAndroidComposeRule<MainActivity>()

    /** Presses system back, as the back gesture does. */
    private fun pressBack() {
        appRule.runOnUiThread { appRule.activity.onBackPressedDispatcher.onBackPressed() }
        appRule.waitForIdle()
    }

    @Test
    fun appOpensOnHome() {
        appRule.onNodeWithTag(rootScreenTag(TrecosTab.Home)).assertIsDisplayed()
    }

    @Test
    fun switchingTabsOpensTheTabScreen() {
        appRule.onNodeWithTag(tabTag(TrecosTab.Search)).performClick()
        appRule.onNodeWithTag(rootScreenTag(TrecosTab.Search)).assertIsDisplayed()
    }

    @Test
    fun screensBelowATabKeepItSelected() {
        appRule.onNodeWithTag(tabTag(TrecosTab.Settings)).performClick()
        appRule.waitUntil(10_000) { appRule.onAllNodesWithTag("setting_currency").fetchSemanticsNodes().isNotEmpty() }
        appRule.onNodeWithTag("setting_currency").performClick()
        appRule.waitUntil(10_000) { appRule.onAllNodesWithTag("currency_search").fetchSemanticsNodes().isNotEmpty() }

        appRule.onNodeWithTag(tabTag(TrecosTab.Settings)).assertIsSelected()
        appRule.onNodeWithTag(tabTag(TrecosTab.Home)).assertIsNotSelected()
    }

    @Test
    fun returningToATabOnASubScreenSelectsIt() {
        appRule.onNodeWithTag(tabTag(TrecosTab.Settings)).performClick()
        appRule.waitUntil(10_000) { appRule.onAllNodesWithTag("setting_currency").fetchSemanticsNodes().isNotEmpty() }
        appRule.onNodeWithTag("setting_currency").performClick()
        appRule.waitUntil(10_000) { appRule.onAllNodesWithTag("currency_search").fetchSemanticsNodes().isNotEmpty() }
        appRule.onNodeWithTag(tabTag(TrecosTab.Search)).performClick()
        appRule.onNodeWithTag(rootScreenTag(TrecosTab.Search)).assertIsDisplayed()

        // Settings comes back on its sub-screen, which must still select the Settings tab.
        appRule.onNodeWithTag(tabTag(TrecosTab.Settings)).performClick()
        appRule.waitUntil(10_000) { appRule.onAllNodesWithTag("currency_search").fetchSemanticsNodes().isNotEmpty() }

        appRule.onNodeWithTag(tabTag(TrecosTab.Settings)).assertIsSelected()
        appRule.onNodeWithTag(tabTag(TrecosTab.Search)).assertIsNotSelected()
    }

    @Test
    fun backFromATabRoot() {
        listOf(TrecosTab.Search, TrecosTab.Settings).forEach { tab ->
            appRule.onNodeWithTag(tabTag(tab)).performClick()
            appRule.onNodeWithTag(rootScreenTag(tab)).assertIsDisplayed()

            pressBack()

            appRule.onNodeWithTag(rootScreenTag(TrecosTab.Home)).assertIsDisplayed()
            assertFalse(appRule.activity.isFinishing)
        }

        pressBack()

        assertTrue(appRule.activity.isFinishing)
    }

    @Test
    fun tabRootsShowNoBackArrow() {
        TrecosTab.entries.forEach { tab ->
            appRule.onNodeWithTag(tabTag(tab)).performClick()
            appRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        }
    }
}

/**
 * Checks the back arrow of the top bar on screens that are not tab roots.
 */
@RunWith(RobolectricTestRunner::class)
class TrecosTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nonRootScreenShowsABackArrowThatGoesBack() {
        var backs = 0
        composeRule.setContent { TrecosTheme { TrecosTopBar(title = "Box A", onBack = { backs++ }) } }

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()

        assertEquals(1, backs)
    }
}
