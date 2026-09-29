package app.trecos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.trecos.ui.theme.TrecosTheme

/**
 * The single activity that hosts every Trecos screen in Compose.
 */
class MainActivity : ComponentActivity() {

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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Text(stringResource(R.string.app_name))
                }
            }
        }
    }
}
