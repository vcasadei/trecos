package app.trecos.ui.places

import androidx.compose.ui.test.hasContentDescription
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tapping Home always goes all the way back to the Home root, however deep the
 * user went into containers and items.
 */
@RunWith(RobolectricTestRunner::class)
class HomeTabTest : PlacesTestBase() {

    private val office = container("office", name = "Office")
    private val boxA = container("boxA", parentId = "office", name = "Box A")
    private val laptop = item("laptop", containerId = "boxA")

    @Test
    fun homeTabLeavesContainersAndItems() {
        seed(containers = listOf(office, boxA), items = listOf(laptop))
        listOf("office", "boxA", "laptop").forEach { click(rowTag(it)) }

        click(tabTag(TrecosTab.Home))

        tag(rowTag("office"))
        eventually { rule.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun homeTabReturnsToTheHouseList() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")), containers = listOf(office))
        showHouseList()
        click(rowTag("h1"))
        click(rowTag("office"))

        click(tabTag(TrecosTab.Home))

        tag(HOUSE_LIST_TAG)
        assertTrue(rule.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isEmpty())
    }
}
