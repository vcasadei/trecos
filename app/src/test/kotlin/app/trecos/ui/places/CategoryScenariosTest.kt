package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import app.trecos.data.CustomCategory
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.data.ItemCategory
import app.trecos.data.ItemTag
import app.trecos.data.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the categories-and-tags spec (tasks 4.3, 4.4, 4.6, 4.7, 4.8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CategoryScenariosTest : PlacesTestBase() {

    /** Opens the new-item form on the current level. */
    private fun newItem() {
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        rule.waitForIdle()
    }

    /**
     * Searches the open picker and taps a category.
     *
     * @param query the search text.
     * @param id the category to tap.
     */
    private fun pick(query: String, id: String) {
        type("category_search_raw", query)
        click(pickTag(id))
    }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** @return the item's category ids, main first. */
    private fun categoriesOf(itemId: String) = runBlocking { app.database.categories().forItem(itemId) }

    /** @return the id of the only item named [name] on the top level of h1. */
    private fun itemNamed(name: String) = runBlocking {
        app.database.items().observeIn("h1", null).first().first { it.name == name }.id
    }

    /** @param id a category; assigns it to the item at [position]. */
    private fun assign(itemId: String, id: String, position: Int) = ItemCategory("$itemId-$id", "h1", itemId, id, position, 1)

    @Test
    fun cableWithTwoEnds() {
        seed()
        newItem()
        type("name", "USB-C to USB-A cable")
        click("add_category")
        pick("usb-c", "cables.usb_c")
        pick("usb-a", "cables.usb_a")
        click("picker_done")
        saveAndClose()

        assertEquals(listOf("cables.usb_c", "cables.usb_a"), categoriesOf(itemNamed("USB-C to USB-A cable")))
    }

    @Test
    fun changingTheMainCategory() {
        seed(items = listOf(item("cable").copy(name = "Cable")))
        runBlocking {
            app.database.categories().replaceForItem("cable", listOf(assign("cable", "cables.usb_c", 0), assign("cable", "cables.hdmi", 1)))
        }
        tag(badgeTag("usb"))
        click(rowTag("cable"))
        clickDescription("Edit")
        click("add_category")
        click("set_main_cables.hdmi")
        click("picker_done")
        saveAndClose()

        assertEquals(listOf("cables.hdmi", "cables.usb_c"), categoriesOf("cable"))
        pressBack()
        tag(badgeTag("settings_input_hdmi")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h800dp-xhdpi")
    fun builtInsInPortuguese() {
        seed()
        newItem()
        click("add_category")
        type("category_search_raw", "usb-c")

        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Cabos > USB-C"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tryingToDeleteABuiltIn() {
        seed()
        newItem()
        click("add_category")
        type("category_search_raw", "usb-c")
        tag(pickTag("cables.usb_c"))

        assertTrue(!exists("category_menu_cables.usb_c"))
    }

    @Test
    fun newSubcategory() {
        seed()
        newItem()
        click("add_category")
        click("new_category")
        tag("new_category_name").performTextInput("Keyboards (mechanical)")
        click("new_category_parent")
        click("parent_computers")
        click("create_category")

        val custom = runBlocking { app.database.categories().custom("h1") }.single()
        assertEquals("computers", custom.parentId)
        assertNull(custom.icon)
        type("category_search_raw", "mechanical")
        tag(pickTag(custom.id))
        rule.onNode(hasTestTag(pickTag(custom.id))).assertTextContains("Computers > Keyboards (mechanical)", substring = true)
    }

    @Test
    fun deletingAUsedCustomCategory() {
        seed(items = (1..5).map { item("k$it").copy(name = "Keyboard $it") })
        runBlocking {
            app.database.categories().insertCustom(CustomCategory("mech", "h1", "computers", "Mechanical", null, 1, 1))
            (1..5).forEach { app.database.categories().replaceForItem("k$it", listOf(assign("k$it", "mech", 0), assign("k$it", "computers.peripherals", 1))) }
        }
        newItem()
        click("add_category")
        type("category_search_raw", "mechanical")
        click("category_menu_mech")
        click("delete_category_mech")
        text("It will be removed from 5 items", substring = true)
        click("confirm_delete_category")

        rule.waitUntil(10_000) { runBlocking { app.database.categories().usage("mech") } == 0 }
        (1..5).forEach { assertEquals(listOf("computers.peripherals"), categoriesOf("k$it")) }
    }

    @Test
    fun otherHousesDontSeeIt() {
        seed(houses = listOf(house("h2", "Parents"), house("h1", "Apartment")))
        runBlocking { app.database.categories().insertCustom(CustomCategory("mech", "h1", "computers", "Mechanical", null, 1, 1)) }
        newItem()
        click("add_category")
        type("category_search_raw", "mechanical")
        rule.waitForIdle()

        assertTrue(!exists(pickTag("mech")))
    }

    @Test
    fun reusingATag() {
        seed(items = listOf(item("drill").copy(name = "Drill")))
        runBlocking {
            app.database.tags().insert(Tag("t1", "h1", "borrowed", "borrowed", 1, 1))
            app.database.tags().insertAssignments(listOf(ItemTag("x", "h1", "drill", "t1", 1)))
        }
        newItem()
        type("name", "Ladder")
        click("more_fields")
        tag(fieldTag("tag")).performTextInput("Borr")
        click("complete_borrowed")
        saveAndClose()

        val ladder = itemNamed("Ladder")
        assertEquals(listOf("borrowed"), runBlocking { app.database.tags().forItem(ladder).map { it.name } })
        assertEquals(1, runBlocking { app.database.tags().all("h1").size })
    }

    @Test
    fun sameTagDifferentCase() {
        seed()
        runBlocking { app.database.tags().insert(Tag("t1", "h1", "borrowed", "borrowed", 1, 1)) }
        newItem()
        type("name", "Ladder")
        click("more_fields")
        tag(fieldTag("tag")).performTextInput("Borrowed")
        click("add_tag")
        saveAndClose()

        assertEquals(listOf("borrowed"), runBlocking { app.database.tags().all("h1").map { it.name } })
        assertEquals(listOf("borrowed"), runBlocking { app.database.tags().forItem(itemNamed("Ladder")).map { it.name } })
    }

    @Test
    fun tagsAreRenamedAndDeletedEverywhere() {
        seed(items = listOf(item("a"), item("b")))
        runBlocking {
            app.database.tags().insert(Tag("t1", "h1", "lent", "lent", 1, 1))
            app.database.tags().insertAssignments(listOf(ItemTag("x", "h1", "a", "t1", 1), ItemTag("y", "h1", "b", "t1", 1)))
        }
        tag(rowTag("a"))
        clickDescription("More options")
        click("menu_tags")
        click("rename_lent")
        type("rename_field_raw", "Emprestado")
        click("confirm_rename")
        tag("tag_row_Emprestado")
        assertEquals(listOf("Emprestado"), runBlocking { app.database.tags().forItem("b").map { it.name } })

        click("delete_Emprestado")
        click("confirm_delete_tag")
        tag("no_tags")
        assertEquals(emptyList<String>(), runBlocking { app.database.tags().forItem("a").map { it.name } })
    }

    @Test
    fun copyingFromAnotherHouse() {
        seed(items = listOf(item("laptop").copy(name = "Laptop")))
        runBlocking {
            app.database.categories().insertCustom(CustomCategory("mech", "h1", null, "Mechanical", "keyboard", 1, 1))
            app.database.categories().insertCustom(CustomCategory("sw", "h1", "mech", "Switches", null, 1, 1))
            app.database.tags().insert(Tag("t1", "h1", "borrowed", "borrowed", 1, 1))
        }
        text("Apartment").performClick()
        click("switch_add_house")
        type("name", "Beach house")
        click("copy_from")
        click("copy_from_h1")
        saveAndClose()

        val beach = runBlocking { app.database.houses().observeAll().first().first { it.name == "Beach house" } }
        val copied = runBlocking { app.database.categories().custom(beach.id) }
        assertEquals(setOf("Mechanical", "Switches"), copied.map { it.name }.toSet())
        val mech = copied.first { it.name == "Mechanical" }
        assertEquals(mech.id, copied.first { it.name == "Switches" }.parentId)
        assertTrue(mech.id != "mech")
        assertEquals(listOf("borrowed"), runBlocking { app.database.tags().all(beach.id).map { it.name } })
        assertEquals(0, runBlocking { app.database.items().observeIn(beach.id, null).first().size })
    }

    @Test
    fun catalogingABoxOfCables() {
        seed(containers = listOf(container("boxA", name = "Box A")))
        click(rowTag("boxA"))
        newItem()
        type("name", "cabo usb-c 1m")
        click(suggestTag("cables.usb_c"))
        tag(chosenTag("cables.usb_c"))
        click("save_new")

        tag(chosenTag("cables.usb_c")).assertIsDisplayed()
        type("name", "cabo usb-c 2m")
        saveAndClose()

        val saved = runBlocking { app.database.items().observeIn("h1", "boxA").first() }
        assertEquals(listOf("cabo usb-c 1m", "cabo usb-c 2m"), saved.map { it.name })
        saved.forEach { assertEquals(listOf("cables.usb_c"), categoriesOf(it.id)) }
    }

    @Test
    fun suggestionsAreNeverAssignedAutomatically() {
        seed()
        newItem()
        type("name", "cabo usb-c para hdmi")
        tag(suggestTag("cables.usb_c"))
        tag(suggestTag("cables.hdmi"))
        assertTrue(!exists(chosenTag("cables.usb_c")))
        saveAndClose()
        assertEquals(emptyList<String>(), categoriesOf(itemNamed("cabo usb-c para hdmi")))
    }

    @Test
    fun itemFormWithCategoriesScreenshot() {
        seed()
        newItem()
        type("name", "cabo usb-c para hdmi 2m")
        click(suggestTag("cables.usb_c"))
        tag(suggestTag("cables.hdmi"))
        rule.onRoot().captureRoboImage("src/test/screenshots/ItemFormCategories_White.png")
    }

    /**
     * Waits until a node containing the text exists.
     *
     * @param value the text.
     * @param substring whether a partial match counts.
     */
    private fun text(value: String, substring: Boolean) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(value, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
