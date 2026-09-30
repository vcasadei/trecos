package app.trecos.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.trecos.places.MatchIn
import app.trecos.places.SearchFilters
import app.trecos.places.SearchScope
import app.trecos.places.SortBy
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How lists show their rows. */
enum class ListView { Condensed, Detailed }

/** Where new photos come from. */
enum class ImageSource { Ask, Camera, Gallery }

/** What adding an item starts with. */
enum class AddFlow { FormFirst, PhotoFirst }

/** When the house colour band is drawn behind the status bar. */
enum class HouseBand { Automatic, Always, Never }

/**
 * Device-only preferences (design D13); nothing here is synced.
 *
 * @property store the DataStore holding the values.
 * @property defaultCurrency the currency used until the user picks one.
 */
class AppPreferences(private val store: DataStore<Preferences>, private val defaultCurrency: String = regionCurrency()) {

    /** The house opened last, or `null` before any house was opened. */
    val lastHouseId: Flow<String?> = store.data.map { it[LAST_HOUSE] }

    /** The list view chosen in any list's top bar; condensed by default. */
    val listView: Flow<ListView> = store.data.map { prefs ->
        prefs[LIST_VIEW]?.let { runCatching { ListView.valueOf(it) }.getOrNull() } ?: ListView.Condensed
    }

    /** The house band setting; automatic (two or more houses) by default. */
    val houseBand: Flow<HouseBand> = store.data.map { prefs ->
        prefs[HOUSE_BAND]?.let { runCatching { HouseBand.valueOf(it) }.getOrNull() } ?: HouseBand.Automatic
    }

    /** Where new photos come from; "ask" until the user remembers a choice. */
    val imageSource: Flow<ImageSource> = store.data.map { prefs ->
        prefs[IMAGE_SOURCE]?.let { runCatching { ImageSource.valueOf(it) }.getOrNull() } ?: ImageSource.Ask
    }

    /** What adding an item starts with; the form by default. */
    val addFlow: Flow<AddFlow> = store.data.map { prefs ->
        prefs[ADD_FLOW]?.let { runCatching { AddFlow.valueOf(it) }.getOrNull() } ?: AddFlow.FormFirst
    }

    /** The search filters and order, kept until "Clear all" (the typed text is never stored). */
    val searchFilters: Flow<SearchFilters> = store.data.map(::readSearchFilters)

    /**
     * Reads the search filters from stored preferences.
     *
     * @param prefs the stored preferences.
     * @return the filters, with defaults for missing values.
     */
    private fun readSearchFilters(prefs: Preferences): SearchFilters =
        SearchFilters(
            scope = prefs[SEARCH_SCOPE]?.let { runCatching { SearchScope.valueOf(it) }.getOrNull() } ?: SearchScope.Items,
            matchIn = prefs[SEARCH_MATCH]?.let { runCatching { MatchIn.valueOf(it) }.getOrNull() } ?: MatchIn.NameAndDescription,
            houses = prefs[SEARCH_HOUSES].orEmpty(),
            categories = prefs[SEARCH_CATEGORIES].orEmpty(),
            tags = prefs[SEARCH_TAGS].orEmpty(),
            sortBy = prefs[SEARCH_SORT]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: SortBy.Name,
            descending = prefs[SEARCH_DESCENDING] ?: false,
        )

    /** The ISO 4217 display currency; the phone region's currency by default. */
    val currency: Flow<String> = store.data.map { it[CURRENCY] ?: defaultCurrency }

    /**
     * Remembers the house the user is in.
     *
     * @param id the house id.
     */
    suspend fun setLastHouse(id: String) = store.edit { it[LAST_HOUSE] = id }

    /**
     * Saves the list view for every list in the app.
     *
     * @param view the chosen view.
     */
    suspend fun setListView(view: ListView) = store.edit { it[LIST_VIEW] = view.name }

    /**
     * Saves the house band setting.
     *
     * @param band the chosen setting.
     */
    suspend fun setHouseBand(band: HouseBand) = store.edit { it[HOUSE_BAND] = band.name }

    /**
     * Saves where new photos come from.
     *
     * @param source the chosen source, or [ImageSource.Ask] to be asked every time.
     */
    suspend fun setImageSource(source: ImageSource) = store.edit { it[IMAGE_SOURCE] = source.name }

    /**
     * Saves what adding an item starts with.
     *
     * @param flow form first or photo first.
     */
    suspend fun setAddFlow(flow: AddFlow) = store.edit { it[ADD_FLOW] = flow.name }

    /**
     * Saves the search filters and order.
     *
     * @param filters the filters.
     */
    suspend fun setSearchFilters(filters: SearchFilters) = updateSearchFilters { filters }

    /**
     * Changes the search filters from their current stored value, in one
     * edit, so quick successive changes never overwrite each other.
     *
     * @param change builds the new filters from the current ones.
     */
    suspend fun updateSearchFilters(change: (SearchFilters) -> SearchFilters) = store.edit {
        val filters = change(readSearchFilters(it))
        it[SEARCH_SCOPE] = filters.scope.name
        it[SEARCH_MATCH] = filters.matchIn.name
        it[SEARCH_HOUSES] = filters.houses
        it[SEARCH_CATEGORIES] = filters.categories
        it[SEARCH_TAGS] = filters.tags
        it[SEARCH_SORT] = filters.sortBy.name
        it[SEARCH_DESCENDING] = filters.descending
    }

    /**
     * Saves the display currency. Amounts are relabelled, never converted.
     *
     * @param code an ISO 4217 code such as `BRL`.
     */
    suspend fun setCurrency(code: String) = store.edit { it[CURRENCY] = code }

    private companion object {
        val LAST_HOUSE = stringPreferencesKey("last_house_id")
        val LIST_VIEW = stringPreferencesKey("list_view")
        val HOUSE_BAND = stringPreferencesKey("house_band")
        val CURRENCY = stringPreferencesKey("currency")
        val IMAGE_SOURCE = stringPreferencesKey("image_source")
        val ADD_FLOW = stringPreferencesKey("add_flow")
        val SEARCH_SCOPE = stringPreferencesKey("search_scope")
        val SEARCH_MATCH = stringPreferencesKey("search_match")
        val SEARCH_HOUSES = stringSetPreferencesKey("search_houses")
        val SEARCH_CATEGORIES = stringSetPreferencesKey("search_categories")
        val SEARCH_TAGS = stringSetPreferencesKey("search_tags")
        val SEARCH_SORT = stringPreferencesKey("search_sort")
        val SEARCH_DESCENDING = booleanPreferencesKey("search_descending")

        /**
         * @return the currency of the phone's region, or USD when the region has none.
         */
        fun regionCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
    }
}
