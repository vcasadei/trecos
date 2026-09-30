package app.trecos.ui.lock

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import app.trecos.data.Fixtures.item
import app.trecos.lock.DeviceSecurity
import app.trecos.lock.LockTimeout
import app.trecos.ui.places.ADD_BUTTON_TAG
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.places.rowTag
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Scenarios of the app-lock spec (tasks 10.1-10.4). The phone's own lock
 * can't be driven in tests, so a fake [DeviceSecurity] answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class AppLockScenariosTest : PlacesTestBase() {

    /** Answers as configured and counts the prompts. */
    private class FakeSecurity : DeviceSecurity {
        var screenLock = true
        var passes = true
        var prompts = 0

        override fun isScreenLockSet() = screenLock

        override fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
            prompts++
            onResult(passes)
        }
    }

    private val fake = FakeSecurity()
    private var now = 1_000_000L

    @Before
    fun useFake() {
        app.security = fake
        app.lock.clock = { now }
    }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Sends the app to the background for a while and brings it back.
     *
     * @param millis how long it stays in the background.
     */
    private fun leaveAndReturn(millis: Long) {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        now += millis
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    /** Turns the lock on as if the user had done it earlier. */
    private fun lockOn(timeout: LockTimeout = LockTimeout.OneMinute) = runBlocking {
        app.preferences.setAppLock(true)
        app.preferences.setLockTimeout(timeout)
    }

    /**
     * Opens Settings and taps a row.
     *
     * @param key the row's key.
     */
    private fun settingsRow(key: String) {
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_$key"))
        click("setting_$key")
    }

    @Test
    fun turningItOn() {
        seed()
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_app_lock"))
        clickUntil("setting_app_lock") { runBlocking { app.preferences.appLock.first() } }

        assertTrue(fake.prompts >= 1)
        assertFalse(exists(LOCK_SCREEN_TAG))
    }

    @Test
    fun phoneWithoutAScreenLock() {
        seed()
        fake.screenLock = false
        settingsRow("app_lock")

        tag("screen_lock_needed")
        assertFalse(runBlocking { app.preferences.appLock.first() })
        assertEquals(0, fake.prompts)
    }

    @Test
    fun returningQuickly() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        lockOn()
        app.lock.unlocked()
        leaveAndReturn(30_000)

        assertFalse(exists(LOCK_SCREEN_TAG))
        tag(rowTag("pi"))

        fake.passes = false
        leaveAndReturn(2 * 60_000)
        rule.waitUntil(10_000) { exists(LOCK_SCREEN_TAG) }
    }

    @Test
    fun failedUnlock() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        lockOn(LockTimeout.Immediately)
        fake.passes = false
        leaveAndReturn(1_000)

        tag(LOCK_SCREEN_TAG)
        click("unlock")
        assertTrue(fake.prompts >= 2)
        assertTrue(exists(LOCK_SCREEN_TAG))
        assertFalse("no inventory while locked", exists(rowTag("pi")))

        fake.passes = true
        click("unlock")
        rule.waitUntil(10_000) { !exists(LOCK_SCREEN_TAG) }
        tag(rowTag("pi"))
    }

    @Test
    fun recentAppsPreview() {
        seed()
        lockOn()
        rule.waitUntil(10_000) { rule.activity.hidingFromRecents }
        runBlocking { app.preferences.setAppLock(false) }
        rule.waitUntil(10_000) { !rule.activity.hidingFromRecents }
    }

    @Test
    fun userRemovedTheirPhonePin() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        lockOn()
        fake.screenLock = false
        leaveAndReturn(10 * 60_000)

        tag("lock_turned_off")
        assertFalse(exists(LOCK_SCREEN_TAG))
        rule.waitUntil(10_000) { !runBlocking { app.preferences.appLock.first() } }
    }

    @Test
    fun noProfile() {
        seed()
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        type("name", "Mouse")
        saveAndClose()

        assertNull(runBlocking { app.preferences.profile.first() })
        assertFalse(exists("profile_name"))
    }

    @Test
    fun deletingTheProfile() {
        seed()
        ShadowLog.clear()
        settingsRow("profile")
        type("profile_name_raw", "Vitor")
        type("profile_email_raw", "someone@example.com")
        click("save_profile")
        rule.waitUntil(10_000) { runBlocking { app.preferences.profile.first() }?.email == "someone@example.com" }

        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_profile"))
        click("setting_profile")
        click("delete_profile")
        click("confirm_delete_profile")
        rule.waitUntil(10_000) { runBlocking { app.preferences.profile.first() } == null }

        val logged = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg}" }
        assertFalse("name in logs", logged.contains("Vitor"))
        assertFalse("e-mail in logs", logged.contains("someone@example.com"))
    }
}
