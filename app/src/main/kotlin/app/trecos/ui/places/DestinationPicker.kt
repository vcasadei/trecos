package app.trecos.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.categories.TextNormalizer
import app.trecos.data.Container
import app.trecos.places.Destination
import app.trecos.places.PlaceTree
import app.trecos.ui.appContainer
import app.trecos.ui.text.SafeText
import kotlinx.coroutines.flow.combine

/**
 * Returns the test tag of a container row in the destination picker.
 *
 * @param id the container id.
 * @return the tag, such as `dest_<id>`.
 */
fun destTag(id: String): String = "dest_$id"

/**
 * Chooses where to move or copy things: browse the hierarchy (starting in
 * [startHouse]), switch houses, or search containers by name. Containers
 * inside [movingContainers] (and those containers themselves) can't be chosen.
 *
 * @param startHouse the house to start in.
 * @param movingContainers containers being moved, which can't receive themselves.
 * @param confirmLabel the confirm button's text, such as "Move here".
 * @param onPick called with the chosen destination.
 * @param onDismiss closes the picker without choosing.
 * @param otherHouses whether the user may switch to another house.
 */
@Composable
fun DestinationPicker(
    startHouse: String,
    movingContainers: Set<String>,
    confirmLabel: String,
    onPick: (Destination) -> Unit,
    onDismiss: () -> Unit,
    otherHouses: Boolean = true,
) {
    val app = appContainer()
    var houseId by rememberSaveable { mutableStateOf(startHouse) }
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var houseMenu by remember { mutableStateOf(false) }
    val houses by app.database.houses().observeAll().collectAsStateWithLifecycle(emptyList())
    val tree by remember(houseId) {
        combine(app.database.containers().observeAllInHouse(houseId), app.database.items().observeTotals(houseId), ::PlaceTree)
    }.collectAsStateWithLifecycle(null)
    val containers by remember(houseId) { app.database.containers().observeAllInHouse(houseId) }.collectAsStateWithLifecycle(emptyList())
    val placeTree = tree ?: return
    val blocked = remember(containers, movingContainers) {
        containers.filter { c -> placeTree.path(c.id).any { it.id in movingContainers } }.map { it.id }.toSet()
    }
    val listed: List<Container> = if (query.isBlank()) {
        containers.filter { it.parentId == current }
    } else {
        val needle = TextNormalizer.normalize(query)
        containers.filter { TextNormalizer.normalize(it.name).contains(needle) }
    }.sortedBy { TextNormalizer.normalize(it.name) }
    val houseName = houses.firstOrNull { it.id == houseId }?.name.orEmpty()
    val currentBlocked = current in blocked

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("destination_picker"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_destination), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Column {
                OutlinedButton(onClick = { houseMenu = true }, enabled = otherHouses, modifier = Modifier.testTag("dest_house")) {
                    Icon(painterResource(R.drawable.ic_expand), contentDescription = null)
                    SafeText(houseName, maxLines = 1)
                }
                DropdownMenu(expanded = houseMenu, onDismissRequest = { houseMenu = false }) {
                    houses.forEach { house ->
                        DropdownMenuItem(
                            text = { SafeText(house.name, maxLines = 1) },
                            onClick = {
                                houseId = house.id
                                current = null
                                houseMenu = false
                            },
                            modifier = Modifier.testTag("dest_house_${house.id}"),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_places)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dest_search"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    IconButton(onClick = { current = placeTree.path(current).dropLast(1).lastOrNull()?.id }, modifier = Modifier.testTag("dest_up")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                }
                SafeText(
                    (listOf(houseName) + placeTree.path(current).map { it.name }).joinToString(" > "),
                    maxLines = 2,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).testTag("dest_path"),
                )
            }
            if (currentBlocked) {
                Text(stringResource(R.string.cannot_move_inside), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("dest_blocked"))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(listed, key = { it.id }) { container ->
                    val isBlocked = container.id in blocked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = container.id
                                query = ""
                            }
                            .padding(vertical = 8.dp)
                            .testTag(destTag(container.id)),
                    ) {
                        Icon(painterResource(PlaceIcons.container(container.icon)), contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            SafeText(
                                container.name,
                                maxLines = 1,
                                color = if (isBlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                            )
                            if (query.isNotBlank()) {
                                SafeText(
                                    placeTree.path(container.parentId).joinToString(" > ") { it.name }.ifEmpty { houseName },
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onPick(Destination(houseId, current)) },
                enabled = !currentBlocked,
                modifier = Modifier.fillMaxWidth().testTag("dest_confirm"),
            ) { Text(confirmLabel) }
        }
    }
}
