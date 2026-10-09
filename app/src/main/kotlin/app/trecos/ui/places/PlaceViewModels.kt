package app.trecos.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.data.ItemFieldValue
import app.trecos.data.DetailExtras
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.ListView
import app.trecos.categories.CategoryCatalog
import app.trecos.places.PlaceTree
import app.trecos.ui.theme.PaletteColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Home tab shows. */
sealed interface HomeState {
    /** Still reading the database. */
    data object Loading : HomeState

    /** No house exists yet: ask for the first one. */
    data object FirstRun : HomeState

    /**
     * Show a house's top level.
     *
     * @property houseId the house to show: the last used, or the first one.
     */
    data class Ready(val houseId: String) : HomeState
}

/**
 * Decides what the Home tab shows and creates the first house.
 *
 * @param app the app's container.
 */
class HomeViewModel(private val app: AppContainer) : ViewModel() {

    /** The Home tab's state, updating as houses change. */
    val state: StateFlow<HomeState> = combine(app.database.houses().observeAll(), app.preferences.lastHouseId) { houses, last ->
        when {
            houses.isEmpty() -> HomeState.FirstRun
            else -> HomeState.Ready((houses.firstOrNull { it.id == last } ?: houses.first()).id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    /**
     * Creates the first house and makes it the current one.
     *
     * @param name the name the user confirmed; blank names are ignored.
     */
    fun createFirstHouse(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val now = app.clock()
            val house = House(
                id = app.newId(), name = trimmed, icon = PlaceIcons.defaultHouse,
                colorKey = PaletteColor.entries.first().key, createdAt = now, updatedAt = now,
            )
            app.database.houses().insert(house)
            app.preferences.setLastHouse(house.id)
        }
    }
}

/**
 * Everything a house's top level or a container screen shows.
 *
 * @property house the house.
 * @property container the container, or `null` on the house's top level.
 * @property houses every house, for the switcher.
 * @property containers the containers directly inside.
 * @property items the items directly inside.
 * @property tree the house's container tree, for values, paths and colours.
 * @property listView the app-wide list view.
 * @property currency the display currency code.
 * @property catalog the house's categories.
 * @property itemCategories each item's category ids, main first.
 * @property mainPhotos each owner's main photo SHA-256, for list rows.
 * @property photos this house's or container's photos, main first.
 * @property extras the detailed view's extras, with each item's tags and custom values.
 */
data class PlaceState(
    val house: House,
    val container: Container?,
    val houses: List<House>,
    val containers: List<Container>,
    val items: List<Item>,
    val tree: PlaceTree,
    val listView: ListView,
    val currency: String,
    val catalog: CategoryCatalog,
    val itemCategories: Map<String, List<String>>,
    val mainPhotos: Map<String, String>,
    val photos: List<String>,
    val extras: DetailData = DetailData(),
)

/**
 * The data behind detailed rows' extras.
 *
 * @property keys the chosen extras.
 * @property tags each item's tag names.
 * @property fields each item's filled-in custom fields.
 */
data class DetailData(
    val keys: List<String> = DetailExtras.default,
    val tags: Map<String, List<String>> = emptyMap(),
    val fields: Map<String, List<ItemFieldValue>> = emptyMap(),
) {
    /**
     * @param itemId an item.
     * @return that item's extras for its row.
     */
    fun forItem(itemId: String) = RowExtras(keys, tags[itemId].orEmpty(), fields[itemId].orEmpty())
}

/**
 * Categories and photos of one place screen.
 *
 * @property catalog the house's categories.
 * @property itemCategories each item's category ids, main first.
 * @property mainPhotos each owner's main photo SHA-256.
 * @property photos the screen's own photos, main first.
 */
private data class PlaceExtras(
    val catalog: CategoryCatalog,
    val itemCategories: Map<String, List<String>>,
    val mainPhotos: Map<String, String>,
    val photos: List<String>,
)

/**
 * Observes the detailed rows' extras of a house's items.
 *
 * @param app the app's container.
 * @param houseId the house.
 * @return the extras, updating as settings, tags and values change.
 */
internal fun detailData(app: AppContainer, houseId: String) = combine(
    app.preferences.detailExtras,
    app.database.tags().observeItemTagNames(houseId),
    app.database.fields().observeForHouse(houseId),
) { keys, tags, fields -> DetailData(keys, tags.groupBy({ it.itemId }, { it.tag }), fields.groupBy { it.itemId }) }

/**
 * Loads a house's top level or one container, and handles its actions.
 *
 * @param app the app's container.
 * @param houseId the house.
 * @param containerId the container, or `null` for the house's top level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceViewModel(private val app: AppContainer, private val houseId: String, private val containerId: String?) : ViewModel() {

    private val db = app.database

    private val contents = combine(
        db.containers().observeChildren(houseId, containerId),
        db.items().observeIn(houseId, containerId),
        combine(db.containers().observeAllInHouse(houseId), db.items().observeTotals(houseId), ::PlaceTree),
    ) { containers, items, tree -> Triple(containers, items, tree) }

    private val categories = combine(
        db.categories().observeCustom(houseId),
        db.categories().observeAssignments(houseId),
        db.photos().observeMainPhotos(houseId),
        db.photos().observeFor(containerId ?: houseId),
    ) { custom, rows, main, own ->
        PlaceExtras(
            CategoryCatalog(app.builtInCategories, custom),
            rows.groupBy({ it.itemId }, { it.categoryId }),
            main.associate { it.ownerId to it.sha256 },
            own.map { it.sha256 },
        )
    }

    /** The screen's state, or `null` while loading or when the house or container is gone. */
    val state: StateFlow<PlaceState?> = combine(
        db.houses().observe(houseId),
        containerId?.let { db.containers().observe(it) } ?: flowOf(null),
        db.houses().observeAll(),
        contents,
        combine(app.preferences.listView, app.preferences.currency, categories, detailData(app, houseId)) { view, currency, cats, detail -> listOf(view, currency, cats, detail) },
    ) { house, container, houses, (containers, items, tree), settings ->
        val view = settings[0] as ListView
        val currency = settings[1] as String
        val cats = settings[2] as PlaceExtras
        val detail = settings[3] as DetailData
        if (house == null || (containerId != null && container == null)) {
            null
        } else {
            PlaceState(house, container, houses, containers, items, tree, view, currency, cats.catalog, cats.itemCategories, cats.mainPhotos, cats.photos, detail)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Remembers this house as the last used, for the Home tab and the band. */
    fun rememberHouse() {
        viewModelScope.launch { app.preferences.setLastHouse(houseId) }
    }

    /**
     * Switches the Home tab to another house.
     *
     * @param id the house to show.
     */
    fun switchHouse(id: String) {
        viewModelScope.launch { app.preferences.setLastHouse(id) }
    }

    /**
     * Saves the list view for every list in the app.
     *
     * @param view the chosen view.
     */
    fun setListView(view: ListView) {
        viewModelScope.launch { app.preferences.setListView(view) }
    }

    /** Removes this container's manual value, going back to the automatic one. */
    fun clearOverride() {
        val id = containerId ?: return
        viewModelScope.launch {
            val container = db.containers().get(id) ?: return@launch
            db.containers().update(container.copy(valueOverride = null, updatedAt = app.clock()))
        }
    }
}

/**
 * Everything the item screen shows.
 *
 * @property item the item.
 * @property house its house.
 * @property houses every house, to decide whether the house pill shows.
 * @property tree the house's container tree, for the location path.
 * @property currency the display currency code.
 * @property catalog the house's categories.
 * @property categories the item's category ids, main first.
 * @property tags the item's tag names.
 * @property photos the item's photos, main first.
 * @property customFields the item's filled-in custom fields, house-wide first.
 */
data class ItemState(
    val item: Item,
    val house: House,
    val houses: List<House>,
    val tree: PlaceTree,
    val currency: String,
    val catalog: CategoryCatalog,
    val categories: List<String>,
    val tags: List<String>,
    val photos: List<String>,
    val customFields: List<ItemFieldValue> = emptyList(),
)

/**
 * Categories, tags and photos of the item screen.
 *
 * @property catalog the house's categories.
 * @property categories the item's category ids, main first.
 * @property tags the item's tag names.
 * @property photos the item's photos, main first.
 */
private data class ItemExtras(val catalog: CategoryCatalog, val categories: List<String>, val tags: List<String>, val photos: List<String>)

/**
 * Loads one item for the item screen.
 *
 * @param app the app's container.
 * @param itemId the item.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModel(private val app: AppContainer, itemId: String) : ViewModel() {

    private val db = app.database

    /** The item screen's state, or `null` while loading or when the item is gone. */
    val state: StateFlow<ItemState?> = db.items().observe(itemId).flatMapLatest { item ->
        if (item == null) {
            flowOf(null)
        } else {
            combine(
                db.houses().observe(item.houseId),
                db.houses().observeAll(),
                combine(db.containers().observeAllInHouse(item.houseId), db.items().observeTotals(item.houseId), ::PlaceTree),
                combine(app.preferences.currency, db.fields().observeForItem(item.id)) { currency, fields -> currency to fields },
                combine(
                    db.categories().observeCustom(item.houseId),
                    db.categories().observeForItem(item.id),
                    db.tags().observeForItem(item.id),
                    db.photos().observeFor(item.id),
                ) { custom, rows, tags, photos ->
                    ItemExtras(CategoryCatalog(app.builtInCategories, custom), rows.map { it.categoryId }, tags.map { it.name }, photos.map { it.sha256 })
                },
            ) { house, houses, tree, (currency, fields), extras ->
                house?.let { ItemState(item, it, houses, tree, currency, extras.catalog, extras.categories, extras.tags, extras.photos, fields) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
