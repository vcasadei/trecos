package app.trecos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.theme.TrecosTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The single activity that hosts every Trecos screen in Compose.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Sets up edge-to-edge drawing and the themed Compose content.
     *
     * @param savedInstanceState the state saved by a previous instance, or `null` on a fresh start.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themes = (application as TrecosApplication).container.preferences.theme
        // Read once before the first frame so a Dark choice never flashes White.
        val initial = runBlocking { themes.first() }
        setContent {
            val theme by themes.collectAsState(initial = initial)
            TrecosTheme(mode = theme) {
                TrecosApp()
            }
        }
    }
}
