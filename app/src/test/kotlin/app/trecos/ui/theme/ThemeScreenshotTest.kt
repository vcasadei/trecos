package app.trecos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests of both themes with every palette tint and band.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h680dp-xhdpi")
class ThemeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun whiteTheme() {
        captureRoboImage { TrecosTheme(mode = ThemeMode.White) { ThemeSheet() } }
    }

    @Test
    fun darkTheme() {
        captureRoboImage { TrecosTheme(mode = ThemeMode.Dark) { ThemeSheet() } }
    }

    @Test
    fun schemesComeFromTokensNotTheWallpaper() {
        var white = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            TrecosTheme(mode = ThemeMode.White) { white = MaterialTheme.colorScheme.background }
            TrecosTheme(mode = ThemeMode.Dark) { dark = MaterialTheme.colorScheme.background }
        }
        composeRule.waitForIdle()
        assertEquals(ColorTokens.WhiteBackground, white)
        assertEquals(ColorTokens.DarkBackground, dark)
    }
}

/**
 * A sample of the theme: text styles on the background, then one row per
 * palette colour showing its tint with body text and its band with band text.
 */
@Composable
private fun ThemeSheet() {
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Trecos", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
        Text("Body text on the background", color = colors.onBackground)
        Text("Muted text on the background", color = colors.onSurfaceVariant)
        PaletteColor.entries.forEach { palette ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = palette.key,
                    color = colors.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .background(palette.tint(dark))
                        .padding(8.dp),
                )
                Text(
                    text = "band",
                    color = palette.onBand,
                    modifier = Modifier
                        .width(96.dp)
                        .background(palette.band)
                        .padding(8.dp),
                )
            }
        }
    }
}
