package app.trecos.ui.places

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.trecos.AppContainer
import app.trecos.MainActivity
import app.trecos.TrecosApplication
import app.trecos.data.Container
import app.trecos.data.Fixtures
import app.trecos.data.House
import app.trecos.data.Item
import kotlinx.coroutines.runBlocking
import org.junit.Rule

/**
 * Shared set-up for place scenario tests: the real activity on an in-memory
 * database, with helpers to seed records and wait for them to show.
 */
abstract class PlacesTestBase {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** The app's container, with its in-memory database. */
    protected val app: AppContainer get() = ApplicationProvider.getApplicationContext<TrecosApplication>().container

    /**
     * Seeds houses, containers and items, and makes the first house the current one.
     *
     * @param houses houses to add.
     * @param containers containers to add.
     * @param items items to add.
     */
    protected fun seed(houses: List<House> = listOf(Fixtures.house()), containers: List<Container> = emptyList(), items: List<Item> = emptyList()) =
        runBlocking {
            houses.forEach { app.database.houses().insert(it) }
            containers.forEach { app.database.containers().insert(it) }
            items.forEach { app.database.items().insert(it) }
            houses.firstOrNull()?.let { app.preferences.setLastHouse(it.id) }
        }

    /**
     * Waits until a node with the tag exists.
     *
     * @param tag the test tag.
     * @return the node.
     */
    protected fun tag(tag: String): SemanticsNodeInteraction {
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return rule.onNodeWithTag(tag, useUnmergedTree = true)
    }

    /**
     * Waits for a field on the item screen and returns it with its label and
     * value merged, as a screen reader reads it.
     *
     * @param label the field's label resource.
     * @return the merged node.
     */
    protected fun detail(label: Int): SemanticsNodeInteraction {
        tag(detailTag(label))
        return rule.onNodeWithTag(detailTag(label))
    }

    /**
     * Waits until a node with exactly this text exists.
     *
     * @param text the text.
     * @return the node.
     */
    protected fun text(text: String): SemanticsNodeInteraction {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        return rule.onNode(hasText(text))
    }

    /**
     * Waits until no node with the text exists.
     *
     * @param text the text.
     */
    protected fun gone(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }
    }

    /**
     * Replaces the text of a form field.
     *
     * @param field the field's short name, or a raw test tag followed by `_raw`.
     * @param value the new text.
     */
    protected fun type(field: String, value: String) {
        val tag = if (field.endsWith("_raw")) field.removeSuffix("_raw") else fieldTag(field)
        tag(tag).performTextClearance()
        rule.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(value)
    }

    /**
     * Taps a node by tag after waiting for it.
     *
     * @param tag the test tag.
     */
    protected fun click(tag: String) {
        val node = tag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    /**
     * Waits for a node with the content description, then taps it.
     *
     * @param description the content description, such as "Edit".
     */
    protected fun clickDescription(description: String) {
        val matcher = androidx.compose.ui.test.hasContentDescription(description)
        rule.waitUntil(10_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(matcher).performClick()
        rule.waitForIdle()
    }

    /** Taps Save on a form expected to be valid, then waits until the form has closed. */
    protected fun saveAndClose() {
        click("save")
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("save"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        rule.waitForIdle()
    }

    /**
     * Waits until some node with the tag shows text containing [expected];
     * needed right after navigating, while the previous screen may still be
     * composed.
     *
     * @param tag the test tag.
     * @param expected the text to wait for.
     */
    protected fun waitForTextIn(tag: String, expected: String) {
        val matcher = androidx.compose.ui.test.hasTestTag(tag).and(androidx.compose.ui.test.hasText(expected, substring = true))
        rule.waitUntil(10_000) { rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Presses system back. */
    protected fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }
}
