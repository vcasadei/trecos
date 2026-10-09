package app.trecos.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import app.trecos.lock.LockTimeout
import app.trecos.data.Profile
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.R
import app.trecos.data.AddFlow
import app.trecos.data.DetailExtras
import app.trecos.data.HouseBand
import app.trecos.data.House
import app.trecos.data.ImageSource
import app.trecos.data.ListView
import app.trecos.data.StartScreen
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import app.trecos.ui.theme.ThemeMode
import java.util.Currency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the Settings tab shows.
 *
 * @property currency the ISO currency code.
 * @property start the start tab.
 * @property theme the theme.
 * @property band the house band setting.
 * @property listView the list view.
 * @property extras the detailed-view extras.
 * @property addFlow what adding an item starts with.
 * @property imageSource where photos come from.
 * @property house the house the per-house rows act on, or `null` before any house exists.
 * @property appLock whether the app lock is on.
 * @property lockTimeout the lock timeout.
 * @property profile the optional profile, or `null`.
 */
data class SettingsState(
    val currency: String,
    val start: StartScreen,
    val theme: ThemeMode,
    val band: HouseBand,
    val listView: ListView,
    val extras: List<String>,
    val addFlow: AddFlow,
    val imageSource: ImageSource,
    val house: House?,
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.OneMinute,
    val profile: Profile? = null,
)

/**
 * Reads and changes the device's settings (settings spec); every change
 * applies at once because screens observe the same preferences.
 *
 * @param app the app's container.
 */
class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    private val prefs = app.preferences

    private val basics = combine(prefs.currency, prefs.startScreen, prefs.theme, prefs.houseBand, prefs.listView) { currency, start, theme, band, list ->
        listOf(currency, start, theme, band, list)
    }
    private val house = combine(prefs.lastHouseId, app.database.houses().observeAll()) { last, houses ->
        houses.firstOrNull { it.id == last } ?: houses.firstOrNull()
    }

    private val security = combine(prefs.appLock, prefs.lockTimeout, prefs.profile) { lock, timeout, profile -> Triple(lock, timeout, profile) }

    /** The settings, or `null` while loading. */
    val state: StateFlow<SettingsState?> = combine(
        basics,
        combine(prefs.detailExtras, prefs.addFlow, prefs.imageSource) { extras, flow, source -> Triple(extras, flow, source) },
        house,
        security,
    ) { b, (extras, flow, source), house, (lock, timeout, profile) ->
        SettingsState(
            currency = b[0] as String, start = b[1] as StartScreen, theme = b[2] as ThemeMode, band = b[3] as HouseBand,
            listView = b[4] as ListView, extras = extras, addFlow = flow, imageSource = source, house = house,
            appLock = lock, lockTimeout = timeout, profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** @param screen the start tab. */
    fun setStart(screen: StartScreen) = launch { prefs.setStartScreen(screen) }

    /** @param mode the theme. */
    fun setTheme(mode: ThemeMode) = launch { prefs.setTheme(mode) }

    /** @param band the house band setting. */
    fun setBand(band: HouseBand) = launch { prefs.setHouseBand(band) }

    /** @param view the list view. */
    fun setListView(view: ListView) = launch { prefs.setListView(view) }

    /** @param flow what adding an item starts with. */
    fun setAddFlow(flow: AddFlow) = launch { prefs.setAddFlow(flow) }

    /** @param source where photos come from. */
    fun setImageSource(source: ImageSource) = launch { prefs.setImageSource(source) }

    /** @param on whether the app lock is on (turning it on is confirmed by the phone's lock first). */
    fun setAppLock(on: Boolean) = launch { prefs.setAppLock(on) }

    /** @param timeout the lock timeout. */
    fun setLockTimeout(timeout: LockTimeout) = launch { prefs.setLockTimeout(timeout) }

    /**
     * Saves the currency, then calls [then]; leaving the screen first would cancel the save.
     *
     * @param code the new ISO currency code, after the user accepted the warning.
     * @param then called once it is saved.
     */
    fun setCurrency(code: String, then: () -> Unit) = launch {
        prefs.setCurrency(code)
        then()
    }

    /**
     * Adds or removes a detailed-view extra; a fourth is refused.
     *
     * @param key the extra.
     */
    fun toggleExtra(key: String) = launch {
        prefs.updateDetailExtras { current ->
            when {
                key in current -> current - key
                current.size < DetailExtras.MAX -> current + key
                else -> current
            }
        }
    }

    /** Runs a preference change. */
    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * Where the Settings rows lead.
 *
 * @property openCurrency opens the currency list.
 * @property openExtras opens the detailed-view extras picker.
 * @property openFields opens a house's custom fields.
 * @property openTags opens a house's tags.
 * @property openTrash opens a house's trash.
 * @property openProfile opens the optional profile.
 * @property openBackup opens export and import.
 * @property openSync opens Google Drive sync.
 */
data class SettingsNavigation(
    val openCurrency: () -> Unit,
    val openExtras: () -> Unit,
    val openFields: (houseId: String) -> Unit,
    val openTags: (houseId: String) -> Unit,
    val openTrash: (houseId: String) -> Unit,
    val openProfile: () -> Unit = {},
    val openBackup: () -> Unit = {},
    val openSync: () -> Unit = {},
)

/**
 * The Settings tab, in the spec's section order. Sections whose features
 * haven't shipped yet (sync, Help, Support) arrive with them.
 *
 * @param nav where the rows lead.
 */
@Composable
fun SettingsScreen(nav: SettingsNavigation) {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.tab_settings), onBack = null)
        val current = state ?: return@Column
        val language = AppLanguage.current()
        val driveSync = appFeatures().driveSync
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance), modifier = Modifier.testTag(SETTINGS_LIST_TAG)) {
            item { Section(R.string.settings_general) }
            item {
                ChoiceRow(
                    "language", R.string.setting_language, language,
                    listOf(AppLanguage.English to stringResource(R.string.language_english), AppLanguage.PortugueseBrazil to stringResource(R.string.language_portuguese_brazil)),
                ) { AppLanguage.apply(it) }
            }
            item {
                LinkRow("currency", R.string.setting_currency, currencyLabel(current.currency, language), nav.openCurrency)
            }
            item {
                ChoiceRow(
                    "start", R.string.setting_start, current.start,
                    listOf(StartScreen.Home to stringResource(R.string.tab_home), StartScreen.Search to stringResource(R.string.tab_search)),
                    vm::setStart,
                )
            }
            item { Section(R.string.settings_appearance) }
            item {
                ChoiceRow(
                    "theme", R.string.setting_theme, current.theme,
                    listOf(
                        ThemeMode.FollowSystem to stringResource(R.string.theme_system),
                        ThemeMode.White to stringResource(R.string.theme_white),
                        ThemeMode.Dark to stringResource(R.string.theme_dark),
                    ),
                    vm::setTheme,
                )
            }
            item {
                ChoiceRow(
                    "band", R.string.setting_band, current.band,
                    listOf(
                        HouseBand.Automatic to stringResource(R.string.band_automatic),
                        HouseBand.Always to stringResource(R.string.band_always),
                        HouseBand.Never to stringResource(R.string.band_never),
                    ),
                    vm::setBand,
                )
            }
            item {
                ChoiceRow(
                    "list_view", R.string.setting_list_view, current.listView,
                    listOf(ListView.Condensed to stringResource(R.string.list_condensed), ListView.Detailed to stringResource(R.string.list_detailed)),
                    vm::setListView,
                )
            }
            item {
                val labels = extraLabels(current.extras)
                LinkRow("extras", R.string.setting_extras, labels.ifEmpty { stringResource(R.string.extras_none) }, nav.openExtras)
            }
            item { Section(R.string.settings_items) }
            item {
                ChoiceRow(
                    "add_flow", R.string.setting_add_flow, current.addFlow,
                    listOf(AddFlow.FormFirst to stringResource(R.string.add_flow_form), AddFlow.PhotoFirst to stringResource(R.string.add_flow_photo)),
                    vm::setAddFlow,
                )
            }
            item {
                ChoiceRow(
                    "image_source", R.string.setting_image_source, current.imageSource,
                    listOf(
                        ImageSource.Camera to stringResource(R.string.source_camera),
                        ImageSource.Gallery to stringResource(R.string.source_gallery),
                        ImageSource.Ask to stringResource(R.string.source_ask),
                    ),
                    vm::setImageSource,
                )
            }
            val house = current.house
            item {
                LinkRow("tags", R.string.setting_tags, house?.name ?: stringResource(R.string.no_house_yet), house?.let { { nav.openTags(it.id) } })
            }
            item { Section(R.string.settings_fields) }
            item {
                LinkRow("fields", R.string.setting_house_fields, house?.name ?: stringResource(R.string.no_house_yet), house?.let { { nav.openFields(it.id) } })
            }
            item { Section(R.string.settings_security) }
            item { AppLockRow(current.appLock, vm) }
            if (current.appLock) {
                item {
                    ChoiceRow(
                        "lock_timeout", R.string.setting_lock_timeout, current.lockTimeout,
                        listOf(
                            LockTimeout.Immediately to stringResource(R.string.timeout_immediately),
                            LockTimeout.OneMinute to stringResource(R.string.timeout_1),
                            LockTimeout.FiveMinutes to stringResource(R.string.timeout_5),
                            LockTimeout.FifteenMinutes to stringResource(R.string.timeout_15),
                        ),
                        vm::setLockTimeout,
                    )
                }
            }
            item {
                val profile = current.profile
                val summary = listOfNotNull(profile?.name, profile?.email).joinToString(" · ").ifEmpty { stringResource(R.string.profile_none) }
                LinkRow("profile", R.string.setting_profile, summary, nav.openProfile)
            }
            item { Section(R.string.settings_backup) }
            if (driveSync) item { SyncRow(nav.openSync) }
            item { LinkRow("backup", R.string.setting_backup, stringResource(R.string.setting_backup_hint), nav.openBackup) }
            item { Section(R.string.settings_trash) }
            item {
                LinkRow("trash", R.string.setting_open_trash, house?.name ?: stringResource(R.string.no_house_yet), house?.let { { nav.openTrash(it.id) } })
            }
            item { Section(R.string.settings_about) }
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                LinkRow("version", R.string.setting_version, version, null)
            }
        }
    }
}

/** @return the feature flags. */
@Composable
private fun appFeatures() = app.trecos.ui.appContainer().features

/**
 * The Sync row: off, or the account, with a badge counting conflicts to resolve.
 *
 * @param onClick opens Sync.
 */
@Composable
private fun SyncRow(onClick: () -> Unit) {
    val sync = app.trecos.ui.appContainer().sync
    val status by sync.status.collectAsStateWithLifecycle()
    val state = status.state
    val conflicts = state.conflicts.size
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("setting_sync")) {
        Text(stringResource(R.string.setting_sync), style = MaterialTheme.typography.bodyLarge)
        Text(
            if (state.connected) stringResource(R.string.setting_sync_on, state.accountEmail) else stringResource(R.string.setting_sync_off),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (conflicts > 0) {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.sync_conflicts_count, conflicts, conflicts),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sync_badge"),
            )
        }
    }
}

/**
 * The app lock switch. Turning it on needs a phone screen lock and a
 * successful unlock; without a screen lock, a note explains and it stays off.
 *
 * @param on whether the lock is on.
 * @param vm the settings.
 */
@Composable
private fun AppLockRow(on: Boolean, vm: SettingsViewModel) {
    val app = app.trecos.ui.appContainer()
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.fragment.app.FragmentActivity
    val title = stringResource(R.string.confirm_app_lock)
    var needsScreenLock by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = on, role = Role.Switch) { wanted ->
                when {
                    !wanted -> vm.setAppLock(false)
                    !app.security.isScreenLockSet() -> needsScreenLock = true
                    activity != null -> app.security.authenticate(activity, title) { passed ->
                        if (passed) {
                            app.lock.unlocked()
                            vm.setAppLock(true)
                        }
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("setting_app_lock"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_app_lock), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.app_lock_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp).testTag("app_lock_switch"))
    }
    if (needsScreenLock) {
        AlertDialog(
            onDismissRequest = { needsScreenLock = false },
            text = { Text(stringResource(R.string.screen_lock_needed), modifier = Modifier.testTag("screen_lock_needed")) },
            confirmButton = { TextButton(onClick = { needsScreenLock = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/**
 * @param code an ISO currency code.
 * @param language the app language.
 * @return "BRL · Brazilian Real" in the app language.
 */
internal fun currencyLabel(code: String, language: AppLanguage): String =
    runCatching { "$code · ${Currency.getInstance(code).getDisplayName(app.trecos.places.Money.localeOf(language))}" }.getOrDefault(code)

/**
 * @param extras the chosen extras.
 * @return their labels joined with commas; custom fields show as their names once loaded.
 */
@Composable
private fun extraLabels(extras: List<String>): String {
    val app = app.trecos.ui.appContainer()
    val names = androidx.compose.runtime.produceState(emptyMap<String, String>(), extras) {
        value = extras.filter { it.startsWith(DetailExtras.CUSTOM_PREFIX) }.associateWith { key ->
            app.database.fields().def(key.removePrefix(DetailExtras.CUSTOM_PREFIX))?.name.orEmpty()
        }
    }.value
    return extras.map { key -> builtInExtraLabel(key) ?: names[key].orEmpty() }.filter(String::isNotEmpty).joinToString(", ")
}

/**
 * @param key a built-in extra key.
 * @return its label, or `null` for a custom field.
 */
@Composable
internal fun builtInExtraLabel(key: String): String? = when (key) {
    DetailExtras.CATEGORIES -> stringResource(R.string.field_categories)
    DetailExtras.TAGS -> stringResource(R.string.field_tags)
    DetailExtras.TOTAL -> stringResource(R.string.extra_total)
    DetailExtras.BRAND -> stringResource(R.string.field_brand)
    DetailExtras.MODEL -> stringResource(R.string.field_model)
    DetailExtras.SERIAL -> stringResource(R.string.field_serial)
    DetailExtras.QR -> stringResource(R.string.field_qr)
    DetailExtras.ADDED -> stringResource(R.string.extra_added)
    DetailExtras.CHANGED -> stringResource(R.string.extra_changed)
    else -> null
}

/**
 * A section heading.
 *
 * @param title the heading.
 */
@Composable
private fun Section(@StringRes title: Int) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).testTag("section_$title"),
        )
    }
}

/**
 * A row with a label and the current value, leading somewhere.
 *
 * @param key the row's test key (`setting_<key>`).
 * @param label the label.
 * @param value the current value.
 * @param onClick opens the row, or `null` for a row that only shows a value.
 */
@Composable
private fun LinkRow(key: String, @StringRes label: Int, value: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("setting_$key"),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        SafeText(value, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("value_$key"))
    }
}

/**
 * A row choosing one of a few options from a menu.
 *
 * @param key the row's test key: the row is `setting_<key>`, each option `option_<key>_<value>`.
 * @param label the label.
 * @param selected the current value.
 * @param options each value with its label.
 * @param onSelect called with the chosen value.
 */
@Composable
private fun <T> ChoiceRow(key: String, @StringRes label: Int, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        LinkRow(key, label, options.firstOrNull { it.first == selected }?.second.orEmpty()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        if (value != selected) onSelect(value)
                    },
                    modifier = Modifier.testTag("option_${key}_$value"),
                )
            }
        }
    }
}

/** Test tag of the Settings list. */
const val SETTINGS_LIST_TAG = "settings_list"
