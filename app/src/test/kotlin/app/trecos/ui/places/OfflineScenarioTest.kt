package app.trecos.ui.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Scenario "Using the app in airplane mode" for everything shipped so far
 * (0.2: adding and editing houses, containers and items).
 */
@RunWith(RobolectricTestRunner::class)
class OfflineScenarioTest : PlacesTestBase() {

    @Test
    fun usingTheAppInAirplaneMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).setDefaultNetworkActive(false)
        shadowOf(connectivity).setActiveNetworkInfo(null)
        assertNull(connectivity.activeNetwork)

        click("continue")
        click(ADD_BUTTON_TAG)
        text("Container").performClick()
        type("name", "Office")
        saveAndClose()
        click(rowTag(containerId("Office")))

        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        type("name", "Laptop")
        type("price", "3500")
        saveAndClose()
        click(rowTag(itemId("Laptop")))
        clickDescription("Edit")
        type("quantity", "2")
        saveAndClose()

        text("7,000.00", substring = true).assertIsDisplayed()
        val laptop = runBlocking { app.database.items().get(itemId("Laptop"))!! }
        assertEquals(2, laptop.quantity)
    }

    @Test
    fun theAppDoesNotAskForInternetAccessYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
    }

    /**
     * @param name a container name.
     * @return the id of the first container with that name.
     */
    private fun containerId(name: String): String {
        eventually(10_000) { runBlocking { app.database.containers().observeChildren(houseId(), null).first().any { it.name == name } } }
        return runBlocking { app.database.containers().observeChildren(houseId(), null).first().first { it.name == name }.id }
    }

    /**
     * @param name an item name.
     * @return the id of the first item with that name in the house.
     */
    private fun itemId(name: String): String = runBlocking {
        val house = houseId()
        val containers = app.database.containers().observeAllInHouse(house).first().map { it.id } + listOf<String?>(null)
        containers.firstNotNullOf { id -> app.database.items().observeIn(house, id).first().firstOrNull { it.name == name }?.id }
    }

    /** @return the id of the only house. */
    private fun houseId(): String = runBlocking { app.database.houses().observeAll().first().single().id }

    /**
     * Waits until a node containing the text exists.
     *
     * @param value the text.
     * @param substring whether a partial match counts.
     * @return the node.
     */
    private fun text(value: String, substring: Boolean) = run {
        eventually(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText(value, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(androidx.compose.ui.test.hasText(value, substring = substring), useUnmergedTree = true)
    }
}
