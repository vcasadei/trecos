package app.trecos.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.performTextInput
import app.trecos.R
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.data.ItemCategory
import app.trecos.data.ItemTag
import app.trecos.data.Tag
import app.trecos.places.SearchFilters
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.places.rowTag
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import app.trecos.ui.text.HOUSE_PILL_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the search spec in the app (tasks 7.2-7.8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SearchScenariosTest : PlacesTestBase() {

    /** Opens the Search tab. */
    private fun openSearch() = click(tabTag(TrecosTab.Search))

    /** Types into the search field. */
    private fun typeQuery(text: String) {
        tag(SEARCH_FIELD_TAG).performTextInput(text)
        rule.waitForIdle()
    }

    /**
     * Scrolls the filters list to a value and ticks it.
     *
     * @param key the list key.
     * @param rowTag the row's test tag.
     */
    private fun pickFilter(key: String, rowTag: String) {
        tag("filters_list").performScrollToKey(key)
        click(rowTag)
    }

    /** Waits until the count reads [text]. */
    private fun count(text: String) = waitForTextIn(RESULT_COUNT_TAG, text)

    /** @return the result ids on screen, in order. */
    private fun resultIds(): List<String> = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("row") { node ->
        node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith("row_")
    }, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("row_") }

    @Test
    fun browsingEverything() {
        seed(items = (1..5).map { item("i$it").copy(name = "Thing $it") }, containers = listOf(container("box", name = "Box")))
        openSearch()

        count("5 items")
        tag(SEARCH_FIELD_TAG).assertIsNotFocused()
    }

    @Test
    fun noResultsOffersToClearFilters() {
        seed(items = listOf(item("a").copy(name = "Alpha")))
        runBlocking { app.preferences.setSearchFilters(SearchFilters(tags = setOf("broken"))) }
        openSearch()

        count("0 items")
        tag("no_results").assertIsDisplayed()
        click("clear_filters")
        count("1 item")
    }

    @Test
    fun accentInsensitive() {
        seed(items = listOf(item("head").copy(name = "Cabeça de impressão"), item("x").copy(name = "Mouse")))
        openSearch()
        typeQuery("cabeca")

        count("1 item")
        tag(rowTag("head")).assertIsDisplayed()
    }

    @Test
    fun partialWords() {
        seed(items = listOf(item("pi4").copy(name = "Raspberry Pi 4"), item("pi3").copy(name = "Raspberry Pi 3")))
        openSearch()
        typeQuery("rasp 4")

        count("1 item")
        assertEquals(listOf("pi4"), resultIds())
    }

    @Test
    fun findingBySerialNumber() {
        seed(items = listOf(item("router").copy(name = "Router", serial = "SN4478X21")))
        openSearch()
        click("match")
        click("match_Name")
        typeQuery("SN4478")

        count("1 item")
    }

    @Test
    fun containersOnly() {
        seed(containers = listOf(container("box", name = "Big box")), items = listOf(item("b").copy(name = "Boxed cable")))
        openSearch()
        click("scope_Containers")
        typeQuery("box")

        count("1 result")
        assertEquals(listOf("box"), resultIds())
    }

    @Test
    fun anyWithinAllAcross() {
        seed(items = listOf(item("pi").copy(name = "Pi"), item("usb").copy(name = "USB"), item("drill").copy(name = "Drill")))
        runBlocking {
            app.database.categories().insertAssignments(listOf(
                ItemCategory("1", "h1", "pi", "computers.sbcs", 0, 1), ItemCategory("2", "h1", "usb", "cables.usb_c", 0, 1),
                ItemCategory("3", "h1", "drill", "computers.sbcs", 0, 1),
            ))
            app.database.tags().insert(Tag("t", "h1", "borrowed", "borrowed", 1, 1))
            app.database.tags().insertAssignments(listOf(ItemTag("a", "h1", "pi", "t", 1), ItemTag("b", "h1", "usb", "t", 1)))
        }
        openSearch()
        click("filters")
        pickFilter("c_computers.sbcs", "filter_category_computers.sbcs")
        pickFilter("c_cables.usb_c", "filter_category_cables.usb_c")
        pickFilter("t_borrowed", "filter_tag_borrowed")
        click("filters_done")

        count("2 items")
        // The count updates before the result rows recompose.
        eventually(10_000) { resultIds() == listOf("pi", "usb") }
    }

    @Test
    fun topLevelIncludesSubcategories() {
        seed(items = listOf(item("hdmi").copy(name = "HDMI"), item("usb").copy(name = "USB"), item("pi").copy(name = "Pi")))
        runBlocking {
            app.database.categories().insertAssignments(listOf(
                ItemCategory("1", "h1", "hdmi", "cables.hdmi", 0, 1), ItemCategory("2", "h1", "usb", "cables.usb_c", 0, 1),
                ItemCategory("3", "h1", "pi", "computers.sbcs", 0, 1),
            ))
            app.preferences.setSearchFilters(SearchFilters(categories = setOf("cables")))
        }
        openSearch()

        count("2 items")
        assertEquals(listOf("hdmi", "usb"), resultIds())
    }

    @Test
    fun mostExpensiveFirst() {
        seed(items = listOf(item("cheap", unitPrice = 100).copy(name = "Cheap"), item("none").copy(name = "Unpriced"), item("dear", unitPrice = 90_000).copy(name = "Dear")))
        openSearch()
        click("sort")
        click("sort_UnitPrice")
        click("sort_direction")

        eventually(10_000) { resultIds() == listOf("dear", "cheap", "none") }
    }

    @Test
    fun sameBoxNameInTwoHouses() {
        seed(
            houses = listOf(house("h1", "Apartment").copy(colorKey = "mint"), house("h2", "Parents").copy(colorKey = "rose")),
            containers = listOf(container("a1", name = "Box A", houseId = "h1"), container("a2", name = "Box A", houseId = "h2")),
            items = listOf(item("c1", containerId = "a1", houseId = "h1").copy(name = "USB-C cable"), item("c2", containerId = "a2", houseId = "h2").copy(name = "USB-C cable")),
        )
        openSearch()
        typeQuery("usb")

        count("2 items")
        rule.onNode(hasTestTag(HOUSE_PILL_TAG).and(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("path_c1"))), useUnmergedTree = true).assertTextContains("Apartment")
        rule.onNode(hasTestTag(HOUSE_PILL_TAG).and(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("path_c2"))), useUnmergedTree = true).assertTextContains("Parents")
        assertEquals(2, rule.onAllNodes(hasText("Box A"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun searchingInsideABox() {
        seed(
            containers = listOf(container("boxA", name = "Box A"), container("bag", parentId = "boxA", name = "Bag"), container("other", name = "Other")),
            items = listOf(
                item("u1", containerId = "boxA").copy(name = "USB hub"), item("u2", containerId = "bag").copy(name = "USB cable"),
                item("u3", containerId = "other").copy(name = "USB stick"),
            ),
        )
        click(rowTag("boxA"))
        clickDescription("More options")
        click("menu_${R.string.action_search_here}")
        tag("within_chip").assertIsDisplayed()
        typeQuery("usb")

        count("2 items")
        assertEquals(listOf("u2", "u1"), resultIds())
        click("within_chip")
        count("3 items")
    }

    @Test
    fun searchScreenshot() {
        seed(
            houses = listOf(house("h1", "Apartment").copy(colorKey = "mint"), house("h2", "Parents").copy(colorKey = "rose")),
            containers = listOf(container("office", name = "Office", houseId = "h1"), container("a1", parentId = "office", name = "Box A", houseId = "h1"), container("a2", name = "Box A", houseId = "h2")),
            items = listOf(
                item("c1", containerId = "a1", houseId = "h1", unitPrice = 2_990).copy(name = "USB-C cable 1m"),
                item("c2", containerId = "a2", houseId = "h2").copy(name = "USB-C cable 2m"),
                item("hub", containerId = "office", houseId = "h1", unitPrice = 15_900).copy(name = "USB hub 7 ports"),
            ),
        )
        runBlocking { app.preferences.setCurrency("BRL") }
        openSearch()
        typeQuery("usb")
        count("3 items")
        rule.onRoot().captureRoboImage("src/test/screenshots/Search_White.png")
    }

    @Test
    fun filtersStayUntilClearedAcrossRestarts() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")), items = listOf(item("a"), item("b", houseId = "h2")))
        openSearch()
        click("filters")
        click("filter_house_h2")
        click("filters_done")
        count("1 item")

        rule.activityRule.scenario.recreate()
        openSearch()
        count("1 item")
        assertEquals(setOf("h2"), runBlocking { app.preferences.searchFilters.first().houses })
        assertTrue(rule.onAllNodesWithTag("row_b", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
