package app.trecos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the Dark theme is showing, for picking palette tints. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Applies the Trecos theme and motion to [content].
 *
 * @param mode the theme chosen in Settings.
 * @param systemInDarkTheme whether the phone is in night mode; read from the system by default.
 * @param content the UI to theme.
 */
@Composable
fun TrecosTheme(
    mode: ThemeMode = ThemeMode.FollowSystem,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(systemInDarkTheme)
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalMotion provides rememberSystemMotion()) {
        MaterialTheme(
            colorScheme = if (dark) ColorTokens.DarkScheme else ColorTokens.WhiteScheme,
            content = content,
        )
    }
}
