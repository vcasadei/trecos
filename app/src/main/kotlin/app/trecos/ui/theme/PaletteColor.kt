package app.trecos.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The fixed pastel palette a house or container can take.
 *
 * Every colour has a tint for each theme, which body text sits on, and a
 * deeper band variant for the house status-bar band, which [onBand] sits on.
 * All pairings keep a contrast of at least 4.5:1.
 *
 * @property key the stable identifier stored in the database; never rename it.
 * @property light the tint used in the White theme.
 * @property dark the tint used in the Dark theme.
 * @property band the deeper variant behind the status bar.
 */
enum class PaletteColor(val key: String, val light: Color, val dark: Color, val band: Color) {
    Rose("rose", Color(0xFFF8D7DD), Color(0xFF4A2A31), Color(0xFFB03A55)),
    Coral("coral", Color(0xFFFADCD0), Color(0xFF4A2C22), Color(0xFFB04A2E)),
    Amber("amber", Color(0xFFF9E6C4), Color(0xFF46361C), Color(0xFF8A5A00)),
    Lemon("lemon", Color(0xFFF5EDB8), Color(0xFF3F3A1A), Color(0xFF7A6A00)),
    Lime("lime", Color(0xFFE3EEC4), Color(0xFF2F3B1C), Color(0xFF5A7A1A)),
    Mint("mint", Color(0xFFCFEBDC), Color(0xFF1F3F31), Color(0xFF2A7A55)),
    Teal("teal", Color(0xFFCBE8E6), Color(0xFF1B3D3C), Color(0xFF1F7470)),
    Sky("sky", Color(0xFFD2E6F5), Color(0xFF1E3547), Color(0xFF2A6A9A)),
    Blue("blue", Color(0xFFD6DDF7), Color(0xFF252F4F), Color(0xFF3F55B0)),
    Lavender("lavender", Color(0xFFE2D9F5), Color(0xFF33294A), Color(0xFF6A4AB0)),
    Orchid("orchid", Color(0xFFF0D6EE), Color(0xFF432843), Color(0xFF9A3A90)),
    Stone("stone", Color(0xFFE6E1DA), Color(0xFF37332E), Color(0xFF6A6158));

    /** Text and icon colour on the [band] variant, in both themes. */
    val onBand: Color get() = Color.White

    /**
     * Returns the tint for the current theme.
     *
     * @param darkTheme whether the Dark theme is showing.
     * @return [dark] in the Dark theme, otherwise [light].
     */
    fun tint(darkTheme: Boolean): Color = if (darkTheme) dark else light

    companion object {
        /**
         * Finds a colour by its stored [key].
         *
         * @param key the value stored in the database.
         * @return the matching colour, or `null` for an unknown or missing key.
         */
        fun fromKey(key: String?): PaletteColor? = entries.firstOrNull { it.key == key }
    }
}
