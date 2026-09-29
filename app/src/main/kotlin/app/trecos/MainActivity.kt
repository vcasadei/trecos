package app.trecos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.theme.TrecosTheme

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
        setContent {
            TrecosTheme {
                TrecosApp()
            }
        }
    }
}
