package app.trecos.ui.shell

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.trecos.ui.theme.ThemeMode
import app.trecos.ui.theme.TrecosTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scenarios of the app-shell "Bottom navigation bar" requirement.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h200dp-xhdpi")
class TrecosBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selected by mutableStateOf(TrecosTab.Home)

    /** Shows the bar at the bottom of a themed screen, starting on Home. */
    private fun showBar() {
        composeRule.setContent {
            TrecosTheme(mode = ThemeMode.White) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TrecosBottomBar(selected, onSelect = { selected = it }, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    /**
     * Returns the horizontal centre of a node.
     *
     * @param tag the node's test tag.
     * @return the centre, in dp from the root's start edge.
     */
    private fun centerX(tag: String): Dp =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }

    /**
     * Sets the system animator duration scale.
     *
     * @param scale the value for `ANIMATOR_DURATION_SCALE`.
     */
    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun switchingTabs() {
        setAnimatorScale(1f)
        showBar()

        composeRule.onNodeWithTag(tabTag(TrecosTab.Search)).performClick()
        composeRule.waitForIdle()

        assertEquals(TrecosTab.Search, selected)
        composeRule.onNodeWithTag(tabTag(TrecosTab.Search)).assertIsSelected()
        composeRule.onNodeWithTag(tabTag(TrecosTab.Home)).assertIsNotSelected()
        composeRule.onAllNodesWithTag(TAB_INDICATOR_TAG).assertCountEquals(1)
        assertEquals(centerX(tabTag(TrecosTab.Search)).value, centerX(TAB_INDICATOR_TAG).value, 0.5f)
    }

    @Test
    fun circleArrivesWithin250Ms() {
        setAnimatorScale(1f)
        showBar()
        composeRule.mainClock.autoAdvance = false

        selectWithoutAdvancing(TrecosTab.Settings)
        composeRule.mainClock.advanceTimeBy(48)
        assertNotEquals(centerX(tabTag(TrecosTab.Settings)).value, centerX(TAB_INDICATOR_TAG).value, 0.5f)

        composeRule.mainClock.advanceTimeBy(250)
        assertEquals(centerX(tabTag(TrecosTab.Settings)).value, centerX(TAB_INDICATOR_TAG).value, 0.5f)
    }

    @Test
    fun reducedMotion() {
        setAnimatorScale(0f)
        showBar()
        composeRule.mainClock.autoAdvance = false

        selectWithoutAdvancing(TrecosTab.Search)
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertEquals(centerX(tabTag(TrecosTab.Search)).value, centerX(TAB_INDICATOR_TAG).value, 0.5f)
    }

    @Test
    fun screenshotsOfEachTabInBothThemes() {
        setAnimatorScale(1f)
        listOf(ThemeMode.White, ThemeMode.Dark).forEach { mode ->
            TrecosTab.entries.forEach { tab ->
                captureRoboImage("src/test/screenshots/TrecosBottomBar_${mode.name}_${tab.name}.png") {
                    TrecosTheme(mode = mode) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            TrecosBottomBar(tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }

    /**
     * Selects a tab from the UI thread without letting the clock advance.
     *
     * @param tab the tab to select.
     */
    private fun selectWithoutAdvancing(tab: TrecosTab) {
        composeRule.runOnUiThread {
            selected = tab
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }
}
