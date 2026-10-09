package app.trecos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The fixed colour tokens of the White and Dark themes. Colours never come
 * from the wallpaper (no Material You dynamic colour).
 */
object ColorTokens {
    /** Off-white background of the White theme. */
    val WhiteBackground = Color(0xFFFAF8F5)

    /** Raised surfaces (bars, sheets) in the White theme. */
    val WhiteSurface = Color(0xFFFFFFFF)

    /** Body text in the White theme. */
    val WhiteText = Color(0xFF1C1B1A)

    /** Secondary text in the White theme. */
    val WhiteTextMuted = Color(0xFF5C5955)

    /** Dividers and outlines in the White theme. */
    val WhiteOutline = Color(0xFFD9D5CF)

    /** Pure-black background of the Dark theme, so OLED pixels are off. */
    val DarkBackground = Color(0xFF000000)

    /** Raised surfaces (bars, sheets) in the Dark theme. */
    val DarkSurface = Color(0xFF121212)

    /** Body text in the Dark theme. */
    val DarkText = Color(0xFFEDEBE8)

    /** Secondary text in the Dark theme. */
    val DarkTextMuted = Color(0xFFA8A49F)

    /** Dividers and outlines in the Dark theme. */
    val DarkOutline = Color(0xFF2E2C2A)

    /** Accent for primary actions in the White theme. */
    val Accent = Color(0xFF3F55B0)

    /** Accent used on the Dark theme, lighter so it reads on black. */
    val AccentOnDark = Color(0xFFB9C4F2)

    /** Neon outline of floating bars in the White theme. */
    val WhiteGlow = Color(0xFF651FFF)

    /** Neon outline of floating bars in the Dark theme. */
    val DarkGlow = Color(0xFFB388FF)

    /** Error colour in the White theme. */
    val WhiteError = Color(0xFFB3261E)

    /** Error colour in the Dark theme. */
    val DarkError = Color(0xFFF2B8B5)

    /** The Material colour scheme of the White theme. */
    val WhiteScheme: ColorScheme = lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        background = WhiteBackground,
        onBackground = WhiteText,
        surface = WhiteSurface,
        onSurface = WhiteText,
        surfaceVariant = WhiteBackground,
        onSurfaceVariant = WhiteTextMuted,
        outline = WhiteOutline,
        error = WhiteError,
    )

    /** The Material colour scheme of the Dark theme. */
    val DarkScheme: ColorScheme = darkColorScheme(
        primary = AccentOnDark,
        onPrimary = Color(0xFF14204F),
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurface,
        onSurfaceVariant = DarkTextMuted,
        outline = DarkOutline,
        error = DarkError,
    )
}
