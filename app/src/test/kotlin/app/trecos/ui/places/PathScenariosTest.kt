package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.item
import app.trecos.ui.text.BREADCRUMB_COLLAPSED_TAG
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scenarios of the places "Location path" requirement and app-shell "Leaving a container".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PathScenariosTest : PlacesTestBase() {

    @Test
    fun jumpingUpTheHierarchy() {
        seed(
            containers = listOf(
                container("office", name = "Office"),
                container("boxA", parentId = "office", name = "Box A"),
                container("cables", parentId = "boxA", name = "Cables bag"),
            ),
        )
        listOf("office", "boxA", "cables").forEach { click(rowTag(it)) }
        text("Box A").assertIsDisplayed()

        text("Office").performClick()
        rule.waitForIdle()

        text("Containers (1)").assertIsDisplayed()
        tag(rowTag("boxA")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun deepPathOnASmallScreen() {
        val names = listOf("Escritório", "Armário grande", "Prateleira de cima", "Caixa organizadora", "Saquinho", "Box A", "Cables bag")
        val chain = names.mapIndexed { i, name -> container("c$i", parentId = if (i == 0) null else "c${i - 1}", name = name) }
        seed(containers = chain)
        chain.forEach { click(rowTag(it.id)) }

        tag(BREADCRUMB_COLLAPSED_TAG).assertIsDisplayed()
        text("Box A").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Escritório").fetchSemanticsNodes().isEmpty())

        click(BREADCRUMB_COLLAPSED_TAG)
        tag("full_path").assertIsDisplayed()
        rule.onNode(hasText("Apartment", substring = true), useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText("Escritório", substring = true), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun leavingAContainer() {
        seed(
            containers = listOf(container("office", name = "Office")),
            items = (10..40).map { item("i$it", containerId = "office").copy(name = "Item $it") },
        )
        click(rowTag("office"))
        tag(PLACE_LIST_TAG).performScrollToKey("i35")
        click(rowTag("i35"))
        text("Quantity")

        pressBack()

        tag(rowTag("i35")).assertIsDisplayed()
    }
}
