package app.trecos.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.places.Money
import app.trecos.places.Selection
import app.trecos.places.totalValue
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.Breadcrumb
import app.trecos.ui.text.SafeText
import app.trecos.ui.theme.PaletteColor
import java.text.DateFormat
import java.util.Date

/**
 * Returns the test tag of a field on the item screen.
 *
 * @param label the field's label resource.
 * @return the tag, such as `detail_<id>`.
 */
fun detailTag(label: Int): String = "detail_$label"

/**
 * Formats a date in the app language.
 *
 * @param epochMillis the time.
 * @param language the app language.
 * @return a medium date such as "30 de set. de 2026" or "Sep 30, 2026".
 */
fun formatDate(epochMillis: Long, language: AppLanguage): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Money.localeOf(language)).format(Date(epochMillis))

/**
 * One item: its location path, then every field that has a value, the total
 * value and the dates it was added and last changed.
 *
 * @param itemId the item.
 * @param nav navigation actions.
 */
@Composable
fun ItemScreen(itemId: String, nav: PlaceNavigation) {
    val vm = appViewModel(key = "item/$itemId") { ItemViewModel(it, itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(state) { if (state != null) loaded = true else if (loaded) nav.back() }
    val organize = rememberOrganizeController()
    val current = state ?: return
    val item = current.item
    val language = AppLanguage.current()
    var fullPathOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var comingLater by rememberSaveable { mutableStateOf(false) }
    val path = current.tree.path(item.containerId)
    val levels = listOf(current.house.name) + path.map { it.name } + item.name
    val fields = listOfNotNull(
        current.categories.takeIf { it.isNotEmpty() }?.let { ids ->
            R.string.field_categories to ids.mapNotNull { current.catalog.label(it, language) }.joinToString("\n")
        },
        R.string.field_quantity to item.quantity.toString(),
        item.unitPrice?.let { R.string.field_unit_price to Money.format(it, current.currency, language) },
        totalValue(item.quantity, item.unitPrice)?.let { R.string.total_value to Money.format(it, current.currency, language) },
        item.brand?.let { R.string.field_brand to it },
        item.model?.let { R.string.field_model to it },
        item.serial?.let { R.string.field_serial to it },
        item.qrCode?.let { R.string.field_qr to it },
        item.description?.let { R.string.field_description to it },
        current.tags.takeIf { it.isNotEmpty() }?.let { R.string.field_tags to it.joinToString(", ") },
        R.string.date_added to formatDate(item.createdAt, language),
        R.string.date_changed to formatDate(item.updatedAt, language),
    )
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = item.name, onBack = nav.back) {
            IconButton(onClick = { nav.editItem(item.id) }) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val here = Selection(itemIds = listOf(item.id))
                    listOf<Pair<Int, () -> Unit>>(
                        R.string.action_move to { organize.move(here, item.houseId) },
                        R.string.action_copy to { organize.copy(here, item.houseId) },
                        R.string.action_duplicate to { organize.duplicate(item.id) { nav.editItem(it) } },
                        R.string.action_delete to { organize.delete(here, item.name) },
                    ).forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menuOpen = false
                                action()
                            },
                            modifier = Modifier.testTag("menu_$label"),
                        )
                    }
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current.photos.isNotEmpty()) {
                item {
                    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
                    PhotoCarousel(current.photos) { viewing = it }
                    viewing?.let { PhotoViewer(current.photos, it) { viewing = null } }
                }
            }
            item {
                val houseColour = PaletteColor.fromKey(current.house.colorKey) ?: PaletteColor.Stone
                Breadcrumb(
                    levels = levels,
                    onLevelClick = { index ->
                        when {
                            index == 0 -> nav.openHouse(current.house.id)
                            index <= path.size -> nav.openContainer(current.house.id, path[index - 1].id)
                        }
                    },
                    onCollapsedClick = { fullPathOpen = true },
                    housePill = if (current.houses.size > 1) houseColour.band else null,
                    housePillText = houseColour.onBand,
                )
            }
            items(fields) { (label, value) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag(detailTag(label)),
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SafeText(value, maxLines = if (label == R.string.field_description || label == R.string.field_categories) 20 else 2, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    OrganizeDialogs(organize, onChooseWhatToKeep = nav.keep)
    if (comingLater) {
        AlertDialog(
            onDismissRequest = { comingLater = false },
            confirmButton = { TextButton(onClick = { comingLater = false }) { Text(stringResource(R.string.close)) } },
            text = { Text(stringResource(R.string.coming_later)) },
        )
    }
    if (fullPathOpen) {
        AlertDialog(
            onDismissRequest = { fullPathOpen = false },
            confirmButton = { TextButton(onClick = { fullPathOpen = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.full_path)) },
            text = { Column(Modifier.padding(4.dp)) { levels.forEachIndexed { i, name -> Text("${"  ".repeat(i)}$name") } } },
        )
    }
}
