package app.trecos.ui.theme

/**
 * The theme the user picked in Settings.
 */
enum class ThemeMode {
    /** Follows the phone's night mode; the default. */
    FollowSystem,

    /** The off-white light theme, labelled "White". */
    White,

    /** The pure-black theme for OLED screens. */
    Dark;

    /**
     * Resolves this mode to a concrete light or dark theme.
     *
     * @param systemInDarkTheme whether the phone is currently in night mode.
     * @return `true` when the Dark theme should be shown.
     */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        FollowSystem -> systemInDarkTheme
        White -> false
        Dark -> true
    }
}
