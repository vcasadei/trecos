package app.trecos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks that every preference keeps what was saved and falls back to its default.
 */
class AppPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs by lazy {
        AppPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") },
            defaultCurrency = "BRL",
        )
    }

    @After
    fun stop() = scope.cancel()

    @Test
    fun defaults() = runBlocking {
        assertEquals(null, prefs.lastHouseId.first())
        assertEquals(ListView.Condensed, prefs.listView.first())
        assertEquals(HouseBand.Automatic, prefs.houseBand.first())
        assertEquals("BRL", prefs.currency.first())
    }

    @Test
    fun savedValuesAreKept() = runBlocking {
        prefs.setLastHouse("h2")
        prefs.setListView(ListView.Detailed)
        prefs.setHouseBand(HouseBand.Never)
        prefs.setCurrency("EUR")

        assertEquals("h2", prefs.lastHouseId.first())
        assertEquals(ListView.Detailed, prefs.listView.first())
        assertEquals(HouseBand.Never, prefs.houseBand.first())
        assertEquals("EUR", prefs.currency.first())
    }
}
