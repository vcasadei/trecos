package app.trecos.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How lists show their rows. */
enum class ListView { Condensed, Detailed }

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

        /**
         * @return the currency of the phone's region, or USD when the region has none.
         */
        fun regionCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
    }
}
