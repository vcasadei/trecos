package app.trecos.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.trecos.R
import app.trecos.ui.appContainer

/** Test tag of the locked screen. */
const val LOCK_SCREEN_TAG = "lock_screen"

/**
 * Shows [content] only while the app is unlocked. While locked, the content
 * leaves the composition (its state is kept) and only an Unlock button shows.
 *
 * @param content the app.
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val app = appContainer()
    val locked by app.lock.locked.collectAsState()
    val notice by app.lock.turnedOffNotice.collectAsState()
    val holder = rememberSaveableStateHolder()
    if (locked) LockScreen() else holder.SaveableStateProvider("app") { content() }
    if (notice) {
        AlertDialog(
            onDismissRequest = app.lock::noticeSeen,
            text = { Text(stringResource(R.string.lock_turned_off), modifier = Modifier.testTag("lock_turned_off")) },
            confirmButton = { TextButton(onClick = app.lock::noticeSeen) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/**
 * The locked screen: nothing from the inventory, only the app name and an
 * Unlock button. The prompt opens once by itself; cancelling or failing keeps the app locked.
 */
@Composable
private fun LockScreen() {
    val app = appContainer()
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.unlock_title)
    val unlock = {
        activity?.let { app.security.authenticate(it, title) { passed -> if (passed) app.lock.unlocked() } }
        Unit
    }
    LaunchedEffect(Unit) { unlock() }
    Surface(Modifier.fillMaxSize().testTag(LOCK_SCREEN_TAG), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.locked_message), style = MaterialTheme.typography.titleMedium)
                Button(onClick = unlock, modifier = Modifier.testTag("unlock")) { Text(stringResource(R.string.action_unlock)) }
            }
        }
    }
}
