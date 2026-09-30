package app.trecos.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.trecos.lock.LockTimeout
import app.trecos.places.MatchIn
import app.trecos.places.SearchFilters
import app.trecos.places.SearchScope
import app.trecos.places.SortBy
import app.trecos.ui.theme.ThemeMode
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
 * The optional profile (spec "Optional profile"): used only to label sync
 * records and, later, house sharing. Never logged.
 *
 * @property name the name, or `null`.
 * @property email the e-mail address, or `null`.
 */
data class Profile(val name: String?, val email: String?) {
    /** Keeps the values out of logs and crash reports. */
    override fun toString(): String = "Profile(name=${if (name == null) "none" else "set"}, email=${if (email == null) "none" else "set"})"
}

/** The tab the app opens on. */
enum class StartScreen { Home, Search }

/**
 * The extra fields a detailed row can show (spec "Detailed-view extra fields").
 * Custom fields are stored as [CUSTOM_PREFIX] plus the field id.
 */
object DetailExtras {
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val TOTAL = "total"
    const val BRAND = "brand"
    const val MODEL = "model"
    const val SERIAL = "serial"
    const val QR = "qr"
    const val ADDED = "added"
    const val CHANGED = "changed"

    /** The prefix of a custom field's key. */
    const val CUSTOM_PREFIX = "field:"

    /** The most extras a row shows. */
    const val MAX = 3

    /** The built-in extras, in the picker's order. */
    val builtIn = listOf(CATEGORIES, TAGS, TOTAL, BRAND, MODEL, SERIAL, QR, ADDED, CHANGED)

    /** The default: categories. */
    val default = listOf(CATEGORIES)
}

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

    /** The theme; follows the system by default. */
    val theme: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.FollowSystem
    }

    /** The tab the app opens on; Home by default. */
    val startScreen: Flow<StartScreen> = store.data.map { prefs ->
        prefs[START_SCREEN]?.let { runCatching { StartScreen.valueOf(it) }.getOrNull() } ?: StartScreen.Home
    }

    /** The detailed view's extra fields, in order; categories by default. */
    val detailExtras: Flow<List<String>> = store.data.map { prefs ->
        prefs[DETAIL_EXTRAS]?.let { stored -> stored.split(',').filter(String::isNotEmpty) } ?: DetailExtras.default
    }

    /** Whether the app lock is on; off by default. */
    val appLock: Flow<Boolean> = store.data.map { it[APP_LOCK] ?: false }

    /** How long the app may be in the background before locking; 1 minute by default. */
    val lockTimeout: Flow<LockTimeout> = store.data.map { prefs ->
        prefs[LOCK_TIMEOUT]?.let { runCatching { LockTimeout.valueOf(it) }.getOrNull() } ?: LockTimeout.OneMinute
    }

    /** The optional profile, or `null` when none was saved. */
    val profile: Flow<Profile?> = store.data.map { prefs ->
        val name = prefs[PROFILE_NAME]
        val email = prefs[PROFILE_EMAIL]
        if (name == null && email == null) null else Profile(name, email)
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
     * Saves the theme.
     *
     * @param mode the chosen theme.
     */
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    /**
     * Saves the tab the app opens on.
     *
     * @param screen the chosen tab.
     */
    suspend fun setStartScreen(screen: StartScreen) = store.edit { it[START_SCREEN] = screen.name }

    /**
     * Saves the detailed view's extras, keeping at most [DetailExtras.MAX].
     *
     * @param extras the chosen extras, in order.
     */
    suspend fun setDetailExtras(extras: List<String>) = updateDetailExtras { extras }

    /**
     * Changes the detailed view's extras from their stored value in one edit,
     * so quick taps never overwrite each other. At most [DetailExtras.MAX] are kept.
     *
     * @param change builds the new extras from the current ones.
     */
    suspend fun updateDetailExtras(change: (List<String>) -> List<String>) = store.edit { prefs ->
        val current = prefs[DETAIL_EXTRAS]?.split(',')?.filter(String::isNotEmpty) ?: DetailExtras.default
        prefs[DETAIL_EXTRAS] = change(current).distinct().take(DetailExtras.MAX).joinToString(",")
    }

    /**
     * Turns the app lock on or off.
     *
     * @param on whether it is on.
     */
    suspend fun setAppLock(on: Boolean) = store.edit { it[APP_LOCK] = on }

    /**
     * Saves the lock timeout.
     *
     * @param timeout the chosen timeout.
     */
    suspend fun setLockTimeout(timeout: LockTimeout) = store.edit { it[LOCK_TIMEOUT] = timeout.name }

    /**
     * Saves the profile; blank fields are removed.
     *
     * @param name the name, or blank.
     * @param email the e-mail address, or blank.
     */
    suspend fun setProfile(name: String, email: String) = store.edit { prefs ->
        name.trim().ifEmpty { null }?.let { prefs[PROFILE_NAME] = it } ?: prefs.remove(PROFILE_NAME)
        email.trim().ifEmpty { null }?.let { prefs[PROFILE_EMAIL] = it } ?: prefs.remove(PROFILE_EMAIL)
    }

    /** Deletes the profile from the device. */
    suspend fun deleteProfile() = store.edit { prefs ->
        prefs.remove(PROFILE_NAME)
        prefs.remove(PROFILE_EMAIL)
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
        val THEME = stringPreferencesKey("theme")
        val START_SCREEN = stringPreferencesKey("start_screen")
        val DETAIL_EXTRAS = stringPreferencesKey("detail_extras")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")

        /**
         * @return the currency of the phone's region, or USD when the region has none.
         */
        fun regionCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
    }
}
