package app.trecos.lock

import app.trecos.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests of when the app locks (spec "When the app locks") and the profile's log safety. */
class AppLockTest {

    @Test
    fun policy() {
        assertFalse(LockPolicy.mustUnlock(enabled = false, backgroundedAt = null, now = 0, timeout = LockTimeout.Immediately))
        assertTrue(LockPolicy.mustUnlock(enabled = true, backgroundedAt = null, now = 0, timeout = LockTimeout.FifteenMinutes))
        assertFalse(LockPolicy.mustUnlock(true, backgroundedAt = 0, now = 30_000, timeout = LockTimeout.OneMinute))
        assertTrue(LockPolicy.mustUnlock(true, backgroundedAt = 0, now = 60_000, timeout = LockTimeout.OneMinute))
        assertTrue(LockPolicy.mustUnlock(true, backgroundedAt = 0, now = 0, timeout = LockTimeout.Immediately))
        assertFalse(LockPolicy.mustUnlock(true, backgroundedAt = 0, now = 4 * 60_000, timeout = LockTimeout.FiveMinutes))
    }

    @Test
    fun returningQuicklyStaysUnlocked() {
        var now = 0L
        val lock = AppLock { now }
        lock.onForeground(enabled = true, timeout = LockTimeout.OneMinute, screenLockSet = true)
        assertTrue(lock.locked.value)
        lock.unlocked()
        lock.onBackground()
        now = 30_000
        lock.onForeground(true, LockTimeout.OneMinute, true)
        assertFalse(lock.locked.value)
        lock.onBackground()
        now = 200_000
        lock.onForeground(true, LockTimeout.OneMinute, true)
        assertTrue(lock.locked.value)
    }

    @Test
    fun screenLockRemovedTurnsItOff() {
        val lock = AppLock { 0 }
        assertTrue(lock.onForeground(enabled = true, timeout = LockTimeout.OneMinute, screenLockSet = false))
        assertFalse(lock.locked.value)
        assertTrue(lock.turnedOffNotice.value)
        lock.noticeSeen()
        assertFalse(lock.turnedOffNotice.value)
    }

    @Test
    fun profileNeverPrintsItsValues() {
        val text = Profile("Vitor", "someone@example.com").toString()
        assertFalse(text.contains("Vitor"))
        assertFalse(text.contains("example.com"))
        assertEquals("Profile(name=none, email=none)", Profile(null, null).toString())
    }
}
