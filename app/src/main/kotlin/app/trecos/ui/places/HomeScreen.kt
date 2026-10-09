package app.trecos.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.ui.appViewModel
import app.trecos.ui.shell.TrecosTopBar

/** Test tag of the first-run prompt. */
const val FIRST_RUN_TAG = "first_run"

/** Test tag of the house list. */
const val HOUSE_LIST_TAG = "house_list"

/** Test tag of the house list's "Add house" row. */
const val HOUSE_LIST_ADD_TAG = "house_list_add"

/**
 * The Home tab: the first-run prompt when there is no house yet, the house's
 * top level when there is one (its name switches houses), and the house list
 * when there are two or more.
 *
 * A pending house (the last used on app start, or one just opened from a
 * path or created) opens above the list once the list contains it; with one
 * house the root already shows it.
 *
 * @param nav navigation actions.
 * @param pendingHouse a house to open, or `null`.
 * @param onPendingHandled called once [pendingHouse] has been handled.
 */
@Composable
fun HomeScreen(nav: PlaceNavigation, pendingHouse: String? = null, onPendingHandled: () -> Unit = {}) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state, pendingHouse) {
        val pending = pendingHouse ?: return@LaunchedEffect
        when (val current = state) {
            is HomeState.Houses -> if (current.houses.any { it.house.id == pending }) {
                onPendingHandled()
                nav.openHouseScreen(pending)
            }
            is HomeState.Ready -> if (current.houseId == pending) onPendingHandled()
            else -> Unit
        }
    }
    when (val current = state) {
        HomeState.Loading -> Box(Modifier.fillMaxSize())
        HomeState.FirstRun -> {
            androidx.activity.compose.ReportDrawn()
            FirstRunPrompt(onConfirm = vm::createFirstHouse)
        }
        is HomeState.Ready -> PlaceScreen(houseId = current.houseId, containerId = null, nav = nav, isTabRoot = true)
        is HomeState.Houses -> {
            androidx.activity.compose.ReportDrawn()
            HouseList(current, onOpen = nav.openHouseScreen, onAdd = nav.addHouse)
        }
    }
}

/**
 * The house list: every house with its value, then "Add house".
 *
 * @param state the houses.
 * @param onOpen opens a house's top level.
 * @param onAdd opens the new-house form.
 */
@Composable
private fun HouseList(state: HomeState.Houses, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.houses_title), onBack = null)
        LazyColumn(
            contentPadding = PaddingValues(bottom = BottomBarClearance),
            modifier = Modifier.fillMaxSize().testTag(HOUSE_LIST_TAG),
        ) {
            items(state.houses, key = { it.house.id }) { row ->
                HouseRow(row.house, row.value, state.listView, state.currency, row.photo) { onOpen(row.house.id) }
            }
            item(key = "add") {
                TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).testTag(HOUSE_LIST_ADD_TAG)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.add_house), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
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
