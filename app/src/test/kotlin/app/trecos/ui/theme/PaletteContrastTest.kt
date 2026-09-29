package app.trecos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that every palette tint and theme text pairing meets WCAG AA contrast.
 */
class PaletteContrastTest {

    private val minimum = 4.5

    /**
     * Computes the WCAG contrast ratio between two colours.
     *
     * @param a one colour.
     * @param b the other colour.
     * @return the ratio, from 1.0 (identical) to 21.0 (black on white).
     */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Asserts that [foreground] on [background] reaches [minimum].
     *
     * @param what a description used in the failure message.
     * @param foreground the text colour.
     * @param background the colour behind the text.
     */
    private fun assertReadable(what: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$what: contrast %.2f is below %.1f".format(ratio, minimum), ratio >= minimum)
    }

    @Test
    fun paletteHasTwelveColoursWithUniqueKeys() {
        assertEquals(12, PaletteColor.entries.size)
        assertEquals(12, PaletteColor.entries.map { it.key }.toSet().size)
    }

    @Test
    fun whiteThemeTextIsReadableOnEveryLightTint() {
        PaletteColor.entries.forEach {
            assertReadable("${it.key} light tint", ColorTokens.WhiteText, it.light)
            assertReadable("${it.key} light tint, muted text", ColorTokens.WhiteTextMuted, it.light)
        }
    }

    @Test
    fun darkThemeTextIsReadableOnEveryDarkTint() {
        PaletteColor.entries.forEach {
            assertReadable("${it.key} dark tint", ColorTokens.DarkText, it.dark)
            assertReadable("${it.key} dark tint, muted text", ColorTokens.DarkTextMuted, it.dark)
        }
    }

    @Test
    fun bandContentIsReadableOnEveryBand() {
        PaletteColor.entries.forEach { assertReadable("${it.key} band", it.onBand, it.band) }
    }

    @Test
    fun themeTextIsReadableOnThemeBackgrounds() {
        assertReadable("White background", ColorTokens.WhiteText, ColorTokens.WhiteBackground)
        assertReadable("White background, muted", ColorTokens.WhiteTextMuted, ColorTokens.WhiteBackground)
        assertReadable("White surface", ColorTokens.WhiteText, ColorTokens.WhiteSurface)
        assertReadable("Dark background", ColorTokens.DarkText, ColorTokens.DarkBackground)
        assertReadable("Dark background, muted", ColorTokens.DarkTextMuted, ColorTokens.DarkBackground)
        assertReadable("Dark surface", ColorTokens.DarkText, ColorTokens.DarkSurface)
    }

    @Test
    fun darkThemeBackgroundIsPureBlack() {
        assertEquals(Color(0xFF000000), ColorTokens.DarkBackground)
    }

    @Test
    fun keysRoundTrip() {
        PaletteColor.entries.forEach { assertEquals(it, PaletteColor.fromKey(it.key)) }
        assertEquals(null, PaletteColor.fromKey("unknown"))
        assertEquals(null, PaletteColor.fromKey(null))
    }
}
