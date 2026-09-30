package app.trecos.ui.places

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.R
import app.trecos.categories.TagStore
import app.trecos.data.Tag
import app.trecos.ui.appViewModel
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lists a house's tags and renames or deletes them everywhere.
 *
 * @param app the app's container.
 * @param houseId the house.
 */
class TagsViewModel(app: AppContainer, houseId: String) : ViewModel() {
    private val store = TagStore(app.database.tags(), app.clock, app.newId)

    /** The house's tags by name. */
    val tags: StateFlow<List<Tag>> = app.database.tags().observeAll(houseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Renames a tag on every item; a name equal to another tag merges them.
     *
     * @param tag the tag.
     * @param name the new name.
     */
    fun rename(tag: Tag, name: String) {
        viewModelScope.launch { store.rename(tag, name) }
    }

    /**
     * Deletes a tag from every item.
     *
     * @param tag the tag.
     */
    fun delete(tag: Tag) {
        viewModelScope.launch { store.delete(tag) }
    }
}

/**
 * A house's tags, each with rename and delete.
 *
 * @param houseId the house.
 * @param onBack leaves the screen.
 */
@Composable
fun TagsScreen(houseId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "tags/$houseId") { TagsViewModel(it, houseId) }
    val tags by vm.tags.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.tags_title), onBack = onBack)
        if (tags.isEmpty()) {
            Text(
                stringResource(R.string.no_tags),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).testTag("no_tags"),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance)) {
            items(tags, key = { it.id }) { tag ->
                if (renaming?.id == tag.id) {
                    var name by remember(tag.id) { mutableStateOf(tag.name) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("rename_field"),
                        )
                        TextButton(onClick = {
                            vm.rename(tag, name)
                            renaming = null
                        }, enabled = name.isNotBlank(), modifier = Modifier.testTag("confirm_rename")) { Text(stringResource(R.string.action_save)) }
                        TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("tag_row_${tag.name}"),
                    ) {
                        SafeText(tag.name, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { renaming = tag }, modifier = Modifier.testTag("rename_${tag.name}")) { Text(stringResource(R.string.action_rename)) }
                        TextButton(onClick = { deleting = tag }, modifier = Modifier.testTag("delete_${tag.name}")) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.delete_tag_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(tag)
                    deleting = null
                }, modifier = Modifier.testTag("confirm_delete_tag")) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
