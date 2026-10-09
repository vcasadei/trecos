package app.trecos.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.ui.appViewModel

/** Test tag of the first-run prompt. */
const val FIRST_RUN_TAG = "first_run"

/**
 * The Home tab: the first-run prompt when there is no house yet, otherwise
 * the top level of the last-used house, whose name switches houses.
 *
 * @param nav navigation actions.
 */
@Composable
fun HomeScreen(nav: PlaceNavigation) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val current = state) {
        HomeState.Loading -> Box(Modifier.fillMaxSize())
        HomeState.FirstRun -> {
            androidx.activity.compose.ReportDrawn()
            FirstRunPrompt(onConfirm = vm::createFirstHouse)
        }
        is HomeState.Ready -> PlaceScreen(houseId = current.houseId, containerId = null, nav = nav, isTabRoot = true)
    }
}

/**
 * Asks for the first house's name, with "My home" pre-filled.
 *
 * @param onConfirm called with the confirmed name.
 */
@Composable
private fun FirstRunPrompt(onConfirm: (String) -> Unit) {
    val default = stringResource(R.string.default_house_name)
    var name by rememberSaveable { mutableStateOf(default) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .testTag(FIRST_RUN_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.first_run_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(fieldTag("name")),
        )
        Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("continue")) {
            Text(stringResource(R.string.action_continue))
        }
    }
}
