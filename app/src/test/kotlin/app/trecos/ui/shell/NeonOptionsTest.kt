package app.trecos.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.trecos.ui.theme.ThemeMode
import app.trecos.ui.theme.TrecosTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the neon outline variations offered for review in
 * `docs/design/neon-options.md` (task 2.25). Delete the unchosen ones once
 * the user picks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h200dp-xhdpi")
class NeonOptionsTest {

    private val cyan = Color(0xFF00E5FF)

    private val dark = mapOf(
        "D1_cyan_current" to NeonStyle(cyan),
        "D2_cyan_strong" to NeonStyle(cyan, lineWidth = 2.dp, glowWidth = 14.dp, glowAlpha = 0.85f),
        "D3_cyan_subtle" to NeonStyle(cyan, lineWidth = 1.dp, glowWidth = 5.dp, glowAlpha = 0.35f),
        "D4_cyan_with_cyan_disc" to NeonStyle(cyan, disc = cyan, onDisc = Color(0xFF002B33)),
        "D5_magenta" to NeonStyle(Color(0xFFFF2BD6)),
        "D6_violet" to NeonStyle(Color(0xFFB388FF)),
        "D7_acid_green" to NeonStyle(Color(0xFF39FF14)),
        "D8_amber" to NeonStyle(Color(0xFFFFB300)),
    )

    private val white = mapOf(
        "W1_blue_current" to NeonStyle(Color(0xFF3F55B0)),
        "W2_blue_strong" to NeonStyle(Color(0xFF3F55B0), lineWidth = 2.dp, glowWidth = 14.dp, glowAlpha = 0.6f),
        "W3_deep_cyan" to NeonStyle(Color(0xFF00ACC1)),
        "W4_magenta" to NeonStyle(Color(0xFFC51162)),
        "W5_violet" to NeonStyle(Color(0xFF651FFF)),
        "W6_line_only" to NeonStyle(Color(0xFF3F55B0), glowAlpha = 0f),
    )

    @Test
    fun darkOptions() = dark.forEach { (name, style) -> capture(ThemeMode.Dark, name, style) }

    @Test
    fun whiteOptions() = white.forEach { (name, style) -> capture(ThemeMode.White, name, style) }

    /**
     * Captures the bar on the Home tab in one theme and style.
     *
     * @param mode the theme.
     * @param name the option's file name.
     * @param style the neon style.
     */
    private fun capture(mode: ThemeMode, name: String, style: NeonStyle) {
        captureRoboImage("src/test/screenshots/neon/$name.png") {
            TrecosTheme(mode = mode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TrecosBottomBar(TrecosTab.Home, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter), neon = style)
                }
            }
        }
    }
}
