package app.trecos.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.places.MatchIn
import app.trecos.places.SearchScope
import app.trecos.places.SortBy
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.places.ContainerRow
import app.trecos.ui.places.ItemRow
import app.trecos.ui.places.NO_CATEGORY
import app.trecos.ui.places.PlaceNavigation
import app.trecos.ui.places.LocalQrScanner
import app.trecos.ui.places.ScanDialogs
import app.trecos.ui.places.rememberScanController
import androidx.compose.ui.platform.LocalContext
import app.trecos.ui.text.Breadcrumb
import app.trecos.ui.text.SafeText
import app.trecos.ui.theme.PaletteColor

/** Test tag of the search field. */
const val SEARCH_FIELD_TAG = "search_field"

/** Test tag of the result count. */
const val RESULT_COUNT_TAG = "result_count"

/**
 * The Search tab: a field (the keyboard stays closed until tapped), the QR
 * button, scope, match and sort controls, filters, the live count and the
 * results with their location paths.
 *
 * @param nav navigation actions, to open results.
 */
@Composable
fun SearchScreen(nav: PlaceNavigation) {
    val vm = appViewModel { SearchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsState()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val scan = rememberScanController(nav)
    val scanner = LocalQrScanner.current
    val context = LocalContext.current
    val language = AppLanguage.current()
    val current = state

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(SEARCH_FIELD_TAG),
            )
            IconButton(onClick = { scan.scan(scanner, context) }, modifier = Modifier.testTag("scan_qr")) {
                Icon(painterResource(R.drawable.ic_qr), contentDescription = stringResource(R.string.scan_qr))
            }
        }
        if (current == null) return@Column
        val filters = current.filters
        current.within?.let { container ->
            InputChip(
                selected = true,
                onClick = vm::clearWithin,
                label = { SafeText(container.name, maxLines = 1) },
                trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_remove)) },
                modifier = Modifier.padding(horizontal = 16.dp).testTag("within_chip"),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(SearchScope.Items to R.string.scope_items, SearchScope.Containers to R.string.scope_containers, SearchScope.Both to R.string.scope_both)
                .forEach { (scope, label) ->
                    FilterChip(
                        selected = filters.scope == scope,
                        onClick = { vm.updateFilters { it.copy(scope = scope) } },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.testTag("scope_${scope.name}"),
                    )
                }
            Choice(
                label = stringResource(R.string.match_label),
                options = listOf(MatchIn.NameAndDescription to R.string.match_name_desc, MatchIn.Name to R.string.match_name, MatchIn.Description to R.string.match_desc),
                selected = filters.matchIn,
                tag = "match",
            ) { choice -> vm.updateFilters { it.copy(matchIn = choice) } }
            FilterChip(
                selected = filters.hasFilters,
                onClick = { filtersOpen = true },
                label = { Text(stringResource(R.string.filters) + if (filters.hasFilters) " (${filters.houses.size + filters.categories.size + filters.tags.size})" else "") },
                modifier = Modifier.testTag("filters"),
            )
            Choice(
                label = null,
                options = listOf(SortBy.Name to R.string.sort_name, SortBy.DateAdded to R.string.sort_date, SortBy.UnitPrice to R.string.sort_price),
                selected = filters.sortBy,
                tag = "sort",
            ) { choice -> vm.updateFilters { it.copy(sortBy = choice) } }
            IconButton(onClick = { vm.updateFilters { it.copy(descending = !it.descending) } }, modifier = Modifier.testTag("sort_direction")) {
                Icon(painterResource(R.drawable.ic_expand), contentDescription = stringResource(R.string.sort_direction))
            }
        }
        val count = current.results.size
        Text(
            if (filters.scope == SearchScope.Items) pluralStringResource(R.plurals.item_count, count, count) else pluralStringResource(R.plurals.result_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag(RESULT_COUNT_TAG),
        )
        if (current.results.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("no_results"))
                if (filters.hasFilters) {
                    OutlinedButton(onClick = vm::clearFilters, modifier = Modifier.testTag("clear_filters")) { Text(stringResource(R.string.clear_filters)) }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance), modifier = Modifier.testTag("search_results")) {
            items(current.results, key = { it.id }) { result ->
                val tree = current.trees[result.houseId]
                val house = current.houses[result.houseId]
                Column {
                    result.item?.let { item ->
                        val ids = current.itemCategories[item.id].orEmpty()
                        ItemRow(
                            item = item,
                            listView = current.listView,
                            currency = current.currency,
                            mainIcon = ids.firstOrNull()?.let { current.catalog[it]?.icon } ?: NO_CATEGORY,
                            categoryLabels = ids.mapNotNull { current.catalog.label(it, language) },
                            photo = current.mainPhotos[item.id],
                        ) { nav.openItem(item.id) }
                    }
                    result.container?.let { container ->
                        ContainerRow(
                            container = container,
                            colour = PaletteColor.fromKey(tree?.colorKey(container.id)),
                            value = null,
                            listView = current.listView,
                            currency = current.currency,
                            photo = current.mainPhotos[container.id],
                        ) { nav.openContainer(container.houseId, container.id) }
                    }
                    val parent = result.item?.containerId ?: result.container?.parentId
                    val levels = listOf(house?.name.orEmpty()) + (tree?.path(parent)?.map { it.name } ?: emptyList())
                    val colour = PaletteColor.fromKey(house?.colorKey) ?: PaletteColor.Stone
                    Breadcrumb(
                        levels = levels,
                        onLevelClick = { index ->
                            if (index == 0) nav.openHouse(result.houseId) else tree?.path(parent)?.getOrNull(index - 1)?.let { nav.openContainer(result.houseId, it.id) }
                        },
                        onCollapsedClick = {},
                        housePill = colour.band,
                        housePillText = colour.onBand,
                        keepHouse = true,
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 8.dp).testTag("path_${result.id}"),
                    )
                }
            }
        }
    }
    if (filtersOpen && current != null) FiltersDialog(current, vm) { filtersOpen = false }
    ScanDialogs(scan)
}

/**
 * A dropdown chip choosing one of several options.
 *
 * @param label a prefix shown before the chosen option, or `null`.
 * @param options each option with its label.
 * @param selected the chosen option.
 * @param tag the chip's test tag prefix.
 * @param onSelect called with the chosen option.
 */
@Composable
private fun <T> Choice(label: String?, options: List<Pair<T, Int>>, selected: T, tag: String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text((label?.let { "$it: " } ?: "") + stringResource(options.first { it.first == selected }.second)) },
            modifier = Modifier.testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                    modifier = Modifier.testTag("${tag}_$value"),
                )
            }
        }
    }
}

/**
 * The filters: houses, categories and tags, each allowing several values,
 * with "Clear all".
 *
 * @param state the search state.
 * @param vm the search ViewModel.
 * @param onDone closes the dialog.
 */
@Composable
private fun FiltersDialog(state: SearchState, vm: SearchViewModel, onDone: () -> Unit) {
    val language = AppLanguage.current()
    val filters = state.filters
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(16.dp).testTag("filters_dialog")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::clearFilters, modifier = Modifier.testTag("clear_all")) { Text(stringResource(R.string.clear_all)) }
                TextButton(onClick = onDone, modifier = Modifier.testTag("filters_done")) { Text(stringResource(R.string.action_done)) }
            }
            LazyColumn(Modifier.weight(1f).testTag("filters_list")) {
                item { Heading(stringResource(R.string.filter_houses)) }
                items(state.houses.values.sortedBy { it.name }, key = { "h_${it.id}" }) { house ->
                    CheckRow(house.name, house.id in filters.houses, "filter_house_${house.id}") { checked ->
                        vm.updateFilters { it.copy(houses = if (checked) it.houses + house.id else it.houses - house.id) }
                    }
                }
                item { Heading(stringResource(R.string.field_categories)) }
                items(state.catalog.all, key = { "c_${it.id}" }) { category ->
                    CheckRow(state.catalog.label(category.id, language).orEmpty(), category.id in filters.categories, "filter_category_${category.id}") { checked ->
                        vm.updateFilters { it.copy(categories = if (checked) it.categories + category.id else it.categories - category.id) }
                    }
                }
                if (state.tags.isNotEmpty()) item { Heading(stringResource(R.string.field_tags)) }
                items(state.tags.entries.toList(), key = { "t_${it.key}" }) { (normalized, name) ->
                    CheckRow(name, normalized in filters.tags, "filter_tag_$normalized") { checked ->
                        vm.updateFilters { it.copy(tags = if (checked) it.tags + normalized else it.tags - normalized) }
                    }
                }
            }
        }
    }
}

/**
 * A section heading in the filters dialog.
 *
 * @param text the heading.
 */
@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/**
 * One filter value with a checkbox.
 *
 * @param label the value's name.
 * @param checked whether it is chosen.
 * @param tag the row's test tag.
 * @param onChange called with the new state.
 */
@Composable
private fun CheckRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.testTag(tag)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        SafeText(label, maxLines = 1)
    }
}
