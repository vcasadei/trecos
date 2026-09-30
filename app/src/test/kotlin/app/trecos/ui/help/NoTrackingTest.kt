package app.trecos.ui.help

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.trecos.sync.SyncWorker
import app.trecos.ui.places.ADD_BUTTON_TAG
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.search.SEARCH_FIELD_TAG
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenario "Offline user" of the help-and-support spec (task 14.6): without
 * Google Drive connected, using the app opens no connection at all. Every
 * HTTP(S) connection the JVM makes asks the default [ProxySelector] first, so
 * a recording one catches any attempt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NoTrackingTest : PlacesTestBase() {

    private val attempts = CopyOnWriteArrayList<URI>()
    private var previous: ProxySelector? = null

    @Before
    fun record() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app.appContext, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                attempts += uri
                return listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        })
    }

    @After
    fun restore() = ProxySelector.setDefault(previous)

    @Test
    fun offlineUser() {
        seed()
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        type("name", "Raspberry Pi")
        saveAndClose()
        click(tabTag(TrecosTab.Search))
        tag(SEARCH_FIELD_TAG).performTextInput("rasp")
        text("Raspberry Pi")
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_faq"))
        rule.waitForIdle()

        assertEquals("no connection was opened: $attempts", emptyList<URI>(), attempts.toList())
        assertTrue("no background sync is scheduled", WorkManager.getInstance(app.appContext).getWorkInfosForUniqueWork(SyncWorker.NAME).get().isEmpty())
    }
}
