package app.trecos

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import app.trecos.ui.lock.LockGate
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.theme.TrecosTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The single activity that hosts every Trecos screen in Compose.
 */
class MainActivity : AppCompatActivity() {

    private val container: AppContainer get() = (application as TrecosApplication).container

    /** Whether inventory content is kept out of the recent-apps preview; read by tests. */
    var hidingFromRecents = false
        private set

    /**
     * Sets up edge-to-edge drawing and the themed Compose content behind the app lock.
     *
     * @param savedInstanceState the state saved by a previous instance, or `null` on a fresh start.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themes = container.preferences.theme
        // Read once before the first frame so a Dark choice never flashes White.
        val initial = runBlocking { themes.first() }
        setContent {
            val theme by themes.collectAsState(initial = initial)
            val navController = rememberNavController()
            TrecosTheme(mode = theme) {
                LockGate { TrecosApp(navController) }
            }
        }
        lifecycleScope.launch { container.preferences.appLock.collect(::hideFromRecents) }
    }

    /**
     * Decides whether to lock before anything is drawn (spec "When the app
     * locks"), and turns the lock off when the phone lost its screen lock.
     */
    override fun onStart() {
        super.onStart()
        val prefs = container.preferences
        val (enabled, timeout) = runBlocking { prefs.appLock.first() to prefs.lockTimeout.first() }
        val screenLockGone = container.lock.onForeground(enabled, timeout, container.security.isScreenLockSet())
        if (screenLockGone) lifecycleScope.launch { prefs.setAppLock(false) }
    }

    /** Starts the background timer, except for a rotation or other configuration change. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.lock.onBackground()
    }

    /**
     * Keeps inventory content out of the recent-apps preview while the lock is on.
     *
     * @param lockOn whether the app lock is on.
     */
    private fun hideFromRecents(lockOn: Boolean) {
        hidingFromRecents = lockOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!lockOn)
        } else if (lockOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
