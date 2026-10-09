package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.item
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scenarios of the places "Nested containers", "Container screen" and "Add button" requirements.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ContainerScenariosTest : PlacesTestBase() {

    private val office = container("office", name = "Office")
    private val boxA = container("boxA", parentId = "office", name = "Box A")

    /** Opens a container from the Home tab by tapping its rows in turn. */
    private fun open(vararg ids: String) = ids.forEach { click(rowTag(it)) }

    @Test
    fun boxInsideABox() {
        seed(containers = listOf(office, boxA))
        open("office", "boxA")

        click(ADD_BUTTON_TAG)
        text("Container").performClick()
        type("name", "Cables bag")
        saveAndClose()

        text("Containers (1)").assertIsDisplayed()
        text("Cables bag").assertIsDisplayed()
        val saved = runBlocking { app.database.containers().observeChildren("h1", "boxA").first() }
        assertEquals(listOf("Cables bag"), saved.map { it.name })
    }

    @Test
    fun containerWithoutAName() {
        seed()
        click(ADD_BUTTON_TAG)
        text("Container").performClick()
        click("save")

        tag("error_name").assertIsDisplayed()
        assertEquals(0, runBlocking { app.database.containers().observeChildren("h1", null).first().size })
    }

    @Test
    fun containerWithContents() {
        seed(
            containers = listOf(office, boxA, container("boxB", parentId = "office", name = "Box B")),
            items = (1..12).map { item("i$it", containerId = "office") },
        )
        open("office")

        val containers = text("Containers (2)").getUnclippedBoundsInRoot()
        val items = text("Items (12)").getUnclippedBoundsInRoot()
        assertTrue("Containers above Items", containers.bottom <= items.top)
    }

    @Test
    fun emptyContainer() {
        seed(containers = listOf(office))
        open("office")

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasText("Containers", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasText("Items (", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun addingFromAContainer() {
        seed(containers = listOf(office, boxA))
        open("office", "boxA")

        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        type("name", "USB-C cable")
        saveAndClose()

        text("USB-C cable").assertIsDisplayed()
        val saved = runBlocking { app.database.items().observeIn("h1", "boxA").first() }
        assertEquals(listOf("USB-C cable"), saved.map { it.name })
    }

    @Test
    fun dismissingTheOptions() {
        seed()
        click(ADD_BUTTON_TAG)
        text("Item").assertIsDisplayed()

        click("add_scrim")
        gone("Item")

        click(ADD_BUTTON_TAG)
        text("Container").assertIsDisplayed()
        pressBack()
        gone("Container")
        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        assertEquals(0, runBlocking { app.database.items().observeIn("h1", null).first().size })
    }

    @Test
    fun containerScreenWhite() = captureContainerScreen("White")

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
    fun containerScreenDark() = captureContainerScreen("Dark")

    /**
     * Seeds a coloured office with sub-containers and items, opens it and
     * captures the screen. The theme follows the system, set by the test's qualifiers.
     *
     * @param theme the theme name used in the file name.
     */
    private fun captureContainerScreen(theme: String) {
        seed(
            containers = listOf(
                office.copy(colorKey = "sky", description = "Everything in the home office, including the desk drawers and the shelf."),
                boxA,
                container("boxB", parentId = "office", name = "Box B").copy(colorKey = "coral", icon = "drawer"),
            ),
            items = listOf(
                item("laptop", containerId = "office", unitPrice = 350_000).copy(name = "Laptop"),
                item("screws", containerId = "office", quantity = 200, unitPrice = 10).copy(name = "M3 screws"),
                item("mystery", containerId = "office").copy(name = "Unlabelled cable"),
            ),
        )
        runBlocking { app.preferences.setCurrency("BRL") }
        open("office")
        text("Laptop")
        rule.onRoot().captureRoboImage("src/test/screenshots/ContainerScreen_$theme.png")
    }
}
