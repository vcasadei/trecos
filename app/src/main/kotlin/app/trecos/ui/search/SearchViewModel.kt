package app.trecos.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.categories.CategoryCatalog
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.ListView
import app.trecos.places.PlaceTree
import app.trecos.places.SearchEngine
import app.trecos.places.SearchFilters
import app.trecos.places.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the Search tab shows.
 *
 * @property results the results, in order.
 * @property filters the filters and order.
 * @property houses every house, by id.
 * @property catalog every category of every house, for labels and the filter list.
 * @property tags every tag name (normalised to display name), for the filter list.
 * @property trees each house's container tree, for result paths.
 * @property within the container the search is limited to, or `null`.
 * @property itemCategories each item's category ids, main first.
 * @property mainPhotos each owner's main photo.
 * @property listView the app-wide list view.
 * @property currency the display currency.
 */
data class SearchState(
    val results: List<SearchResult>,
    val filters: SearchFilters,
    val houses: Map<String, House>,
    val catalog: CategoryCatalog,
    val tags: Map<String, String>,
    val trees: Map<String, PlaceTree>,
    val within: Container?,
    val itemCategories: Map<String, List<String>>,
    val mainPhotos: Map<String, String>,
    val listView: ListView,
    val currency: String,
)

/**
 * Runs the Search tab: matching as the user types (debounced, never stored),
 * persistent filters and order, and "Search in this container".
 *
 * @param app the app's container.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val app: AppContainer) : ViewModel() {

    private val db = app.database

    /** The typed text; kept in memory only. */
    val query = MutableStateFlow("")

    private val matched = combine(query.debounce(150), app.preferences.searchFilters.map { it.matchIn }.distinctUntilChanged()) { text, matchIn ->
        SearchEngine.matchExpression(text, matchIn)
    }.flatMapLatest { expression ->
        expression?.let { db.search().observeMatches(it).map { hits -> hits.map { h -> h.refId }.toSet() } } ?: flowOf(null)
    }

    private val data = combine(
        db.search().observeAllItems(),
        db.search().observeAllContainers(),
        db.search().observeAllAssignments(),
        db.search().observeItemTags(),
        db.search().observeAllCustomCategories(),
    ) { items, containers, assignments, itemTags, custom ->
        SearchData(items, containers, assignments.sortedBy { it.position }.groupBy({ it.itemId }, { it.categoryId }), itemTags.groupBy({ it.itemId }, { it.tag }).mapValues { it.value.toSet() }, custom)
    }

    private val lookups = combine(
        db.houses().observeAll(),
        db.search().observeAllTags(),
        db.search().observeAllMainPhotos(),
        combine(app.preferences.listView, app.preferences.currency) { view, currency -> view to currency },
        app.searchWithin,
    ) { houses, tags, photos, (view, currency), within -> Lookups(houses, tags.associate { it.normalized to it.name }, photos.associate { it.ownerId to it.sha256 }, view, currency, within) }

    /** The Search tab's state, or `null` while loading. */
    val state: StateFlow<SearchState?> = combine(data, matched, app.preferences.searchFilters, lookups) { data, matched, filters, lookups ->
        val catalog = CategoryCatalog(app.builtInCategories, data.custom)
        val trees = data.containers.groupBy { it.houseId }.mapValues { (_, list) -> PlaceTree(list, emptyList()) }
        val withinContainer = lookups.within?.let { id -> data.containers.firstOrNull { it.id == id } }
        val withinIds = withinContainer?.let { root ->
            val tree = trees[root.houseId]
            data.containers.filter { c -> c.houseId == root.houseId && tree?.path(c.id)?.any { it.id == root.id } == true }.map { it.id }.toSet()
        }
        SearchState(
            results = SearchEngine.results(data.items, data.containers, matched, filters, data.itemCategories, data.itemTags, catalog, withinIds),
            filters = filters,
            houses = lookups.houses.associateBy { it.id },
            catalog = catalog,
            tags = lookups.tags,
            trees = trees,
            within = withinContainer,
            itemCategories = data.itemCategories,
            mainPhotos = lookups.mainPhotos,
            listView = lookups.listView,
            currency = lookups.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Changes the filters or order from their current stored value.
     *
     * @param change builds the new filters from the current ones.
     */
    fun updateFilters(change: (SearchFilters) -> SearchFilters) {
        viewModelScope.launch { app.preferences.updateSearchFilters(change) }
    }

    /** Clears the house, category and tag filters, keeping scope and order. */
    fun clearFilters() = updateFilters { it.copy(houses = emptySet(), categories = emptySet(), tags = emptySet()) }

    /** Removes the "Search in this container" limit. */
    fun clearWithin() {
        app.searchWithin.value = null
    }
}

/** The candidates searched, loaded together. */
private data class SearchData(
    val items: List<app.trecos.data.Item>,
    val containers: List<Container>,
    val itemCategories: Map<String, List<String>>,
    val itemTags: Map<String, Set<String>>,
    val custom: List<app.trecos.data.CustomCategory>,
)

/** Houses, tags, photos and display settings, loaded together. */
private data class Lookups(
    val houses: List<House>,
    val tags: Map<String, String>,
    val mainPhotos: Map<String, String>,
    val listView: ListView,
    val currency: String,
    val within: String?,
)
