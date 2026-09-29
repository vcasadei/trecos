package app.trecos.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks how each theme option resolves against the phone's night mode.
 */
class ThemeModeTest {

    @Test
    fun followSystemTracksNightMode() {
        assertEquals(true, ThemeMode.FollowSystem.isDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.FollowSystem.isDark(systemInDarkTheme = false))
    }

    @Test
    fun forcedThemesIgnoreNightMode() {
        assertEquals(false, ThemeMode.White.isDark(systemInDarkTheme = true))
        assertEquals(true, ThemeMode.Dark.isDark(systemInDarkTheme = false))
    }
}
