package app.trecos.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.data.ListView
import app.trecos.places.Money
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.Breadcrumb
import app.trecos.ui.text.SafeText
import app.trecos.ui.theme.LocalDarkTheme
import app.trecos.ui.theme.PaletteColor

/** Test tag of the list-view toggle in the top bar. */
const val VIEW_TOGGLE_TAG = "view_toggle"

/** Test tag of the empty-container hint. */
const val EMPTY_HINT_TAG = "empty_hint"

/** Test tag of the place list. */
const val PLACE_LIST_TAG = "place_list"

/**
 * A house's top level or a container: top bar, a header that scrolls away,
 * then its "Containers (n)" and "Items (n)", and the "+" button.
 *
 * @param houseId the house.
 * @param containerId the container, or `null` for the house's top level.
 * @param nav navigation actions.
 * @param isTabRoot whether this is the Home tab's root (no back arrow; the title switches houses).
 */
@Composable
fun PlaceScreen(houseId: String, containerId: String?, nav: PlaceNavigation, isTabRoot: Boolean) {
    val vm = appViewModel(key = "place/$houseId/$containerId") { PlaceViewModel(it, houseId, containerId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(houseId) { vm.rememberHouse() }
    val current = state ?: return
    val dark = LocalDarkTheme.current
    val language = AppLanguage.current()
    val colour = current.container?.let { PaletteColor.fromKey(current.tree.colorKey(it.id)) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var explanation by rememberSaveable { mutableStateOf<Int?>(null) }
    var fullPathOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box {
                TrecosTopBar(
                    title = current.container?.name ?: current.house.name,
                    onBack = if (isTabRoot) null else nav.back,
                    background = colour?.tint(dark) ?: Color.Transparent,
                    onTitleClick = if (isTabRoot) ({ switcherOpen = true }) else null,
                ) {
                    val detailed = current.listView == ListView.Detailed
                    IconButton(
                        onClick = { vm.setListView(if (detailed) ListView.Condensed else ListView.Detailed) },
                        modifier = Modifier.testTag(VIEW_TOGGLE_TAG),
                    ) {
                        Icon(
                            painterResource(if (detailed) R.drawable.ic_view_condensed else R.drawable.ic_view_detailed),
                            contentDescription = stringResource(if (detailed) R.string.view_condensed else R.string.view_detailed),
                        )
                    }
                    IconButton(onClick = {
                        current.container?.let { nav.editContainer(it.id) } ?: nav.editHouse(current.house.id)
                    }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (current.container == null) {
                                val lastHouse = current.houses.size <= 1
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        explanation = if (lastHouse) R.string.delete_last_house else R.string.coming_later
                                    },
                                    modifier = Modifier.testTag("menu_delete"),
                                )
                            } else {
                                listOf(
                                    R.string.action_move, R.string.action_copy, R.string.action_duplicate,
                                    R.string.action_delete, R.string.action_search_here, R.string.action_print_qr,
                                ).forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = {
                                            menuOpen = false
                                            explanation = R.string.coming_later
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                    current.houses.forEach { house ->
                        DropdownMenuItem(
                            text = { SafeText(house.name, maxLines = 1) },
                            leadingIcon = { Icon(painterResource(PlaceIcons.house(house.icon)), contentDescription = null) },
                            onClick = {
                                switcherOpen = false
                                vm.switchHouse(house.id)
                            },
                            modifier = Modifier.testTag("switch_${house.id}"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_house)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        onClick = {
                            switcherOpen = false
                            nav.addHouse()
                        },
                        modifier = Modifier.testTag("switch_add_house"),
                    )
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = BottomBarClearance),
                modifier = Modifier.fillMaxSize().testTag(PLACE_LIST_TAG),
            ) {
                item(key = "header") {
                    PlaceHeader(
                        state = current,
                        tint = colour?.tint(dark),
                        onLevelClick = { index ->
                            val path = current.tree.path(current.container?.id)
                            if (index == 0) nav.openHouse(current.house.id) else path.getOrNull(index - 1)?.let {
                                if (it.id != current.container?.id) nav.openContainer(current.house.id, it.id)
                            }
                        },
                        onShowFullPath = { fullPathOpen = true },
                        onClearOverride = vm::clearOverride,
                        language = language,
                    )
                }
                if (current.containers.isNotEmpty()) {
                    item(key = "containers") { SectionTitle(stringResource(R.string.section_containers, current.containers.size)) }
                    items(current.containers, key = { it.id }) { container ->
                        ContainerRow(
                            container = container,
                            colour = PaletteColor.fromKey(current.tree.colorKey(container.id)),
                            value = current.tree.value(container.id),
                            listView = current.listView,
                            currency = current.currency,
                            onClick = { nav.openContainer(current.house.id, container.id) },
                        )
                    }
                }
                if (current.items.isNotEmpty()) {
                    item(key = "items") { SectionTitle(stringResource(R.string.section_items, current.items.size)) }
                    items(current.items, key = { it.id }) { item ->
                        ItemRow(item, current.listView, current.currency) { nav.openItem(item.id) }
                    }
                }
                if (current.containers.isEmpty() && current.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.empty_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp).testTag(EMPTY_HINT_TAG),
                        )
                    }
                }
            }
        }
        AddSpeedDial(
            onAddItem = { nav.addItem(current.house.id, current.container?.id) },
            onAddContainer = { nav.addContainer(current.house.id, current.container?.id) },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = BottomBarClearance),
        )
    }

    explanation?.let { message ->
        AlertDialog(
            onDismissRequest = { explanation = null },
            confirmButton = { TextButton(onClick = { explanation = null }) { Text(stringResource(R.string.close)) } },
            text = { Text(stringResource(message), modifier = Modifier.testTag("explanation")) },
        )
    }
    if (fullPathOpen) {
        val levels = listOf(current.house.name) + current.tree.path(current.container?.id).map { it.name }
        AlertDialog(
            onDismissRequest = { fullPathOpen = false },
            confirmButton = { TextButton(onClick = { fullPathOpen = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.full_path)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("full_path")) {
                    levels.forEachIndexed { index, name -> Text("${"  ".repeat(index)}$name") }
                }
            },
        )
    }
}

/**
 * The part of a place screen that scrolls away: icon, description, path, QR code and value.
 *
 * @param state the screen's state.
 * @param tint the container's tint, or `null`.
 * @param onLevelClick called with the index of the path level tapped (0 is the house).
 * @param onShowFullPath called when "…" is tapped.
 * @param onClearOverride removes the manual value.
 * @param language the app language, for number formats.
 */
@Composable
private fun PlaceHeader(
    state: PlaceState,
    tint: Color?,
    onLevelClick: (Int) -> Unit,
    onShowFullPath: () -> Unit,
    onClearOverride: () -> Unit,
    language: AppLanguage,
) {
    val container = state.container
    var expanded by rememberSaveable { mutableStateOf(false) }
    val value = container?.let { state.tree.value(it.id) } ?: state.tree.houseValue
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.5f)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceIconBadge(
            icon = container?.let { PlaceIcons.container(it.icon) } ?: PlaceIcons.house(state.house.icon),
            size = 96.dp,
        )
        (container?.description ?: state.house.description)?.let { description ->
            Text(
                text = description,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { expanded = !expanded }.testTag("description"),
            )
        }
        if (container != null) {
            val levels = listOf(state.house.name) + state.tree.path(container.id).map { it.name }
            val houseColour = PaletteColor.fromKey(state.house.colorKey) ?: PaletteColor.Stone
            Breadcrumb(
                levels = levels,
                onLevelClick = onLevelClick,
                onCollapsedClick = onShowFullPath,
                housePill = if (state.houses.size > 1) houseColour.band else null,
                housePillText = houseColour.onBand,
            )
        }
        container?.qrCode?.let {
            SafeText("${stringResource(R.string.field_qr)}: $it", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = stringResource(if (value.manual) R.string.value_manual else R.string.value_automatic)
            SafeText(
                text = "${stringResource(R.string.value_label)}: ${Money.format(value.value, state.currency, language)} · $label",
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f, fill = false).testTag("place_value"),
            )
            if (value.manual) {
                TextButton(onClick = onClearOverride, modifier = Modifier.testTag("clear_override")) {
                    Text(stringResource(R.string.action_clear_override))
                }
            }
        }
        if (value.unpriced > 0) {
            Text(
                text = pluralStringResource(R.plurals.unpriced_items, value.unpriced, value.unpriced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("unpriced_hint"),
            )
        }
    }
}

/**
 * A section heading such as "Containers (2)".
 *
 * @param text the heading.
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
