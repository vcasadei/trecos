package app.trecos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.trecos.categories.BuiltInCategories
import app.trecos.categories.Category
import app.trecos.categories.CategorySuggester
import app.trecos.data.AppPreferences
import app.trecos.data.TrecosDatabase
import app.trecos.places.OrganizeStore
import app.trecos.places.PhotoStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Holds the app's long-lived objects and hands them to ViewModels (design D2:
 * no dependency-injection framework).
 *
 * @param context the application context.
 * @param openDatabase opens the database on first use; tests pass an in-memory one.
 * @property clock the current time in epoch milliseconds; tests pass a fixed clock.
 * @property newId generates record ids; random UUIDs by default.
 */
class AppContainer(
    context: Context,
    openDatabase: () -> TrecosDatabase = { TrecosDatabase.open(context) },
    val clock: () -> Long = System::currentTimeMillis,
    val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The database, opened on first access. */
    val database: TrecosDatabase by lazy(openDatabase)

    /** The built-in category tree, read once from the bundled asset. */
    val builtInCategories: List<Category> by lazy {
        BuiltInCategories.parse(context.assets.open("categories.json").bufferedReader().use { it.readText() })
    }

    /** Category suggestions, with the bundled keyword dictionary read once. */
    val suggester: CategorySuggester by lazy {
        CategorySuggester.parse(context.assets.open("category-keywords.json").bufferedReader().use { it.readText() })
    }

    /** Stored photos and thumbnails in app-private storage. */
    val photoStore: PhotoStore = PhotoStore(context.filesDir, context.contentResolver)

    /** Moving, copying, the trash and house deletion. */
    val organize: OrganizeStore by lazy { OrganizeStore(database, clock, newId) }

    /** Short messages shown app-wide, such as "Deleted" with Undo. */
    val messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)

    /** Device preferences. */
    val preferences: AppPreferences = AppPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
    )
}

/**
 * A short message shown at the bottom of the app.
 *
 * @property text the message.
 * @property undoEntries trash entries restored when the user taps Undo; empty for no Undo.
 */
data class AppMessage(val text: String, val undoEntries: List<String> = emptyList())
