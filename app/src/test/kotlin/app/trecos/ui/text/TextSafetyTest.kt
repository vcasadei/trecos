package app.trecos.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.trecos.ui.theme.ThemeMode
import app.trecos.ui.theme.TrecosTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scenarios of the app-shell "Text never breaks layouts" requirement and the
 * places breadcrumb collapse rule.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TextSafetyTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Reads the text layout of a text node.
     *
     * @return the layout result the node was drawn with.
     */
    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    /**
     * Returns a node's bounds in pixels of the root.
     *
     * @return the node's bounds.
     */
    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    @Test
    fun veryLongName() {
        composeRule.setContent {
            TrecosTheme {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SafeText(StressFixtures.longName, maxLines = 1, modifier = Modifier.weight(1f).testTag("name"))
                    Text("×12", maxLines = 1, modifier = Modifier.testTag("quantity"))
                    Text("R$ 1.234,56", maxLines = 1, modifier = Modifier.testTag("price"))
                }
            }
        }
        val root = composeRule.onRoot().bounds()
        val name = composeRule.onNodeWithTag("name").layout()

        assertEquals(1, name.lineCount)
        assertTrue("the name ends with …", name.isLineEllipsized(0))
        listOf("quantity", "price").forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val layout = node.layout()
            assertEquals(1, layout.lineCount)
            assertFalse("$tag ends with …", layout.isLineEllipsized(0))
            assertTrue("$tag is narrower than its text", node.bounds().width + 1 >= layout.multiParagraph.intrinsics.maxIntrinsicWidth)
            assertTrue("$tag is inside the screen", node.bounds().right <= root.right)
        }
    }

    @Test
    fun longUnbrokenSerialNumber() {
        composeRule.setContent {
            TrecosTheme {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SafeText(StressFixtures.longSerial, maxLines = 2, modifier = Modifier.width(120.dp).testTag("serial"))
                    Text("Model X", maxLines = 1, modifier = Modifier.testTag("next"))
                }
            }
        }
        val serial = composeRule.onNodeWithTag("serial")
        val layout = serial.layout()
        val next = composeRule.onNodeWithTag("next").bounds()

        assertTrue("the serial wraps inside the word", layout.lineCount == 2)
        assertTrue("the serial ends with …", layout.isLineEllipsized(1))
        assertTrue("nothing is hidden behind the serial", serial.bounds().right <= next.left)
        composeRule.onNodeWithTag("next").assertIsDisplayed()
    }

    @Test
    fun deepPathCollapsesToTheLastTwoLevels() {
        var fullPathShown = false
        composeRule.setContent {
            TrecosTheme {
                Breadcrumb(
                    levels = StressFixtures.deepPath,
                    onLevelClick = {},
                    onCollapsedClick = { fullPathShown = true },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        composeRule.onNodeWithText(BREADCRUMB_COLLAPSED).assertIsDisplayed()
        composeRule.onNodeWithText("Box A").assertDoesNotExist()
        composeRule.onNodeWithText("Saquinho de adaptadores").assertIsDisplayed()
        composeRule.onNodeWithText("Cables bag").assertIsDisplayed()
        composeRule.onNodeWithText("Casa dos meus pais").assertDoesNotExist()

        composeRule.onNodeWithTag(BREADCRUMB_COLLAPSED_TAG).performClick()
        assertTrue(fullPathShown)
    }

    @Test
    fun boxACablesBag() {
        val levels = listOf("Apartment", "Office", "Shelf", "Box A", "Cables bag")
        composeRule.setContent {
            TrecosTheme {
                Breadcrumb(levels, onLevelClick = {}, onCollapsedClick = {}, modifier = Modifier.width(160.dp))
            }
        }
        val shown = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .joinToString("") { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals("… > Box A > Cables bag", shown)
    }

    @Test
    fun shortPathShowsEveryLevel() {
        val tapped = mutableListOf<Int>()
        composeRule.setContent {
            TrecosTheme { Breadcrumb(listOf("Apartment", "Office", "Box A"), onLevelClick = { tapped += it }, onCollapsedClick = {}) }
        }
        composeRule.onNodeWithText(BREADCRUMB_COLLAPSED).assertDoesNotExist()
        composeRule.onNodeWithText("Office").performClick()
        assertEquals(listOf(1), tapped)
    }

    @Test
    fun collapseRule() {
        val path = listOf("House", "Office", "Box A", "Cables bag")
        assertEquals(path.size, breadcrumbCrumbs(path, fitsOnOneLine = true).size)
        assertEquals(
            listOf(Crumb.Collapsed, Crumb.Level(2, "Box A"), Crumb.Level(3, "Cables bag")),
            breadcrumbCrumbs(path, fitsOnOneLine = false),
        )
        assertEquals(2, breadcrumbCrumbs(listOf("House", "Office"), fitsOnOneLine = false).size)
    }

    @Test
    fun stressFixturesHaveTheirSizes() {
        assertEquals(200, StressFixtures.longName.length)
        assertEquals(40, StressFixtures.longSerial.length)
        assertFalse(StressFixtures.longSerial.contains(' '))
        assertEquals(8, StressFixtures.deepPath.size)
    }

    @Test
    fun stressSheetInBothThemes() {
        listOf(ThemeMode.White, ThemeMode.Dark).forEach { mode ->
            captureRoboImage("src/test/screenshots/StressFixtures_${mode.name}.png") {
                TrecosTheme(mode = mode) { StressSheet() }
            }
        }
    }
}

/** The stress fixtures laid out as they will appear in lists and headers. */
@Composable
private fun StressSheet() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeText(StressFixtures.longName, maxLines = 1, modifier = Modifier.weight(1f), color = colors.onBackground)
            Text("×12", color = colors.onBackground)
            Text("R$ 1.234,56", color = colors.onBackground)
        }
        SafeText(StressFixtures.longName, maxLines = 2, color = colors.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeText(StressFixtures.longSerial, maxLines = 2, modifier = Modifier.width(120.dp), color = colors.onBackground)
            Text("Model X", color = colors.onSurfaceVariant)
        }
        Breadcrumb(StressFixtures.deepPath, onLevelClick = {}, onCollapsedClick = {})
        StressFixtures.portuguese.forEach { SafeText(it, maxLines = 1, color = colors.onBackground) }
    }
}
