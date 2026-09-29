package app.trecos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource

/**
 * The single activity that hosts every Trecos screen in Compose.
 */
class MainActivity : ComponentActivity() {

    /**
     * Sets up edge-to-edge drawing and the Compose content.
     *
     * @param savedInstanceState the state saved by a previous instance, or `null` on a fresh start.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Text(stringResource(R.string.app_name))
        }
    }
}
