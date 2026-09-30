package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.trecos.R
import app.trecos.data.DetailExtras
import app.trecos.data.FieldDef
import app.trecos.data.FieldValue
import app.trecos.data.Fixtures.item
import app.trecos.data.ItemTag
import app.trecos.data.ListView
import app.trecos.data.Tag
import app.trecos.ui.search.SEARCH_FIELD_TAG
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the custom-fields spec (tasks 9.2-9.5) and the settings
 * "Detailed-view extra fields" requirement (task 9.9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CustomFieldScenariosTest : PlacesTestBase() {

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Adds a house-wide field to house h1.
     *
     * @param id the definition id.
     * @param name the name.
     * @param type the type name.
     * @param unit the unit, or `null`.
     */
    private fun houseField(id: String, name: String, type: String, unit: String? = null) =
        runBlocking { app.database.fields().insertDef(FieldDef(id, "h1", null, name, type, unit, 0, 1, 1)) }

    /**
     * @param name a custom field's name.
     * @return its detail on the item screen, label and value merged.
     */
    private fun detailCustom(name: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        tag("detail_custom_$name")
        return rule.onNodeWithTag("detail_custom_$name")
    }

    /** Opens an item's edit form with "More fields" open. */
    private fun editWithMoreFields(itemId: String, name: String) {
        click(rowTag(itemId))
        clickDescription("Edit")
        waitForField("name", name)
        click("more_fields")
    }

    /**
     * Scrolls the Settings list to a row and taps it.
     *
     * @param key the row's key.
     */
    private fun settingsRow(key: String) {
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_$key"))
        click("setting_$key")
    }

    /**
     * Ticks an extra and waits until the saved list is exactly [expected]. A tap
     * the test runner drops or doubles (seen rarely, only in full runs) is
     * corrected by tapping again, up to three times.
     *
     * @param key the extra.
     * @param expected the extras after the tap.
     */
    private fun pickExtra(key: String, expected: List<String>) {
        repeat(3) {
            if (runBlocking { app.preferences.detailExtras.first() } == expected) return
            click("extra_$key")
            val saved = runCatching { rule.waitUntil(5_000) { runBlocking { app.preferences.detailExtras.first() } == expected } }.isSuccess
            if (saved) return
        }
        throw AssertionError("extras are ${runBlocking { app.preferences.detailExtras.first() }}, expected $expected")
    }

    /** Opens the house's custom fields from Settings. */
    private fun openHouseFields() {
        click(tabTag(TrecosTab.Settings))
        settingsRow("fields")
    }

    @Test
    fun numberWithUnit() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        houseField("ram", "RAM", "Number", "GB")
        editWithMoreFields("pi", "Raspberry Pi")
        type("custom_RAM_raw", "4")
        saveAndClose()

        detailCustom("RAM").assertTextContains("RAM").assertTextContains("4 GB")
    }

    @Test
    fun wrongType() {
        seed()
        houseField("ram", "RAM", "Number", "GB")
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        type("name", "Raspberry Pi")
        click("more_fields")
        type("custom_RAM_raw", "four")
        click("save")

        tag("error_custom_RAM").assertTextContains("Enter a number")
        assertTrue(runBlocking { app.database.items().observeIn("h1", null).first() }.isEmpty())
    }

    @Test
    fun addingAHouseWideField() {
        seed()
        openHouseFields()
        type("new_field_name_raw", "Purchase date")
        click("field_type")
        click("type_Date")
        click("add_field")
        tag("field_row_Purchase date")

        click(tabTag(TrecosTab.Home))
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        click("more_fields")
        tag("custom_Purchase date").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun renamingKeepsValues() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        houseField("bought", "Bought", "Text")
        runBlocking { app.database.fields().insertValue(FieldValue("v", "h1", "pi", "bought", "Amazon", 1, 1)) }
        openHouseFields()
        click("rename_Bought")
        type("rename_field_raw", "Bought at")
        click("confirm_rename")
        tag("field_row_Bought at")

        assertEquals("Amazon", runBlocking { app.database.fields().values("pi").single().value })
    }

    @Test
    fun deletingAUsedField() {
        seed(items = (1..40).map { item("i$it") })
        houseField("date", "Purchase date", "Date")
        runBlocking { (1..40).forEach { app.database.fields().insertValue(FieldValue("v$it", "h1", "i$it", "date", "2026-09-30", 1, 1)) } }
        openHouseFields()
        click("delete_Purchase date")

        tag("delete_field_message").assertTextContains("40 items have a value", substring = true)
        click("cancel_delete_field")
        assertEquals(40, runBlocking { app.database.fields().valueCount("date") })

        click("delete_Purchase date")
        click("confirm_delete_field")
        rule.waitUntil(10_000) { runBlocking { app.database.fields().def("date") } == null }
        assertEquals(0, runBlocking { app.database.fields().valueCount("date") })
    }

    @Test
    fun oneOffField() {
        seed(items = listOf(item("printer").copy(name = "3D printer"), item("mouse").copy(name = "Mouse")))
        editWithMoreFields("printer", "3D printer")
        click("add_item_field")
        type("item_field_name_raw", "Firmware")
        click("confirm_item_field")
        type("custom_Firmware_raw", "Klipper")
        saveAndClose()

        detailCustom("Firmware").assertTextContains("Klipper")
        assertEquals(listOf("Firmware"), runBlocking { app.database.fields().itemFields("printer").map { it.name } })
        assertTrue(runBlocking { app.database.fields().itemFields("mouse") }.isEmpty())
        pressBack()
        click(rowTag("mouse"))
        tag(detailTag(R.string.field_quantity))
        assertTrue(!exists("detail_custom_Firmware"))
    }

    @Test
    fun searchingACustomValue() {
        seed(items = listOf(item("printer").copy(name = "3D printer"), item("mouse").copy(name = "Mouse")))
        runBlocking {
            app.database.fields().insertDef(FieldDef("fw", "h1", "printer", "Firmware", "Text", null, 0, 1, 1))
            app.database.fields().insertValue(FieldValue("v", "h1", "printer", "fw", "Klipper", 1, 1))
        }
        click(tabTag(TrecosTab.Search))
        click("match")
        click("match_Description")
        tag(SEARCH_FIELD_TAG).performTextInput("Klipper")

        rule.waitUntil(10_000) { exists(rowTag("printer")) && !exists(rowTag("mouse")) }
    }

    @Test
    fun pickingExtras() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi", brand = "Raspberry")))
        runBlocking {
            app.preferences.setListView(ListView.Detailed)
            app.database.tags().insert(Tag("t", "h1", "borrowed", "borrowed", 1, 1))
            app.database.tags().insertAssignments(listOf(ItemTag("it", "h1", "pi", "t", 1)))
        }
        click(tabTag(TrecosTab.Settings))
        settingsRow("extras")
        pickExtra(DetailExtras.TAGS, listOf(DetailExtras.CATEGORIES, DetailExtras.TAGS))
        pickExtra(DetailExtras.BRAND, listOf(DetailExtras.CATEGORIES, DetailExtras.TAGS, DetailExtras.BRAND))
        assertEquals(listOf(DetailExtras.CATEGORIES, DetailExtras.TAGS, DetailExtras.BRAND), runBlocking { app.preferences.detailExtras.first() })
        pressBack()
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_extras"))
        waitForTextIn("value_extras", "Categories, Tags, Brand")
        tag("value_extras").assertTextContains("Categories, Tags, Brand")

        click(tabTag(TrecosTab.Home))
        val row = hasTestTag(rowTag("pi")).and(androidx.compose.ui.test.hasText("borrowed", substring = true)).and(androidx.compose.ui.test.hasText("Brand: Raspberry", substring = true))
        rule.waitUntil(10_000) { rule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tooMany() {
        seed()
        runBlocking { app.preferences.setDetailExtras(listOf(DetailExtras.CATEGORIES, DetailExtras.TAGS, DetailExtras.BRAND)) }
        click(tabTag(TrecosTab.Settings))
        settingsRow("extras")
        tag("extras_limit").assertIsDisplayed()
        click("extra_${DetailExtras.MODEL}")

        assertEquals(listOf(DetailExtras.CATEGORIES, DetailExtras.TAGS, DetailExtras.BRAND), runBlocking { app.preferences.detailExtras.first() })
    }
}
