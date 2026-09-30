package app.trecos.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.ListView
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
)

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

    /** The screen's state, or `null` while loading or when the house or container is gone. */
    val state: StateFlow<PlaceState?> = combine(
        db.houses().observe(houseId),
        containerId?.let { db.containers().observe(it) } ?: flowOf(null),
        db.houses().observeAll(),
        contents,
        combine(app.preferences.listView, app.preferences.currency) { view, currency -> view to currency },
    ) { house, container, houses, (containers, items, tree), (view, currency) ->
        if (house == null || (containerId != null && container == null)) {
            null
        } else {
            PlaceState(house, container, houses, containers, items, tree, view, currency)
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
 */
data class ItemState(val item: Item, val house: House, val houses: List<House>, val tree: PlaceTree, val currency: String)

/**
 * Loads one item for the item screen.
 *
 * @param app the app's container.
 * @param itemId the item.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModel(app: AppContainer, itemId: String) : ViewModel() {

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
                app.preferences.currency,
            ) { house, houses, tree, currency -> house?.let { ItemState(item, it, houses, tree, currency) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
