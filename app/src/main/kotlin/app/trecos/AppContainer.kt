package app.trecos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.trecos.categories.BuiltInCategories
import app.trecos.categories.Category
import app.trecos.categories.CategorySuggester
import app.trecos.data.AppPreferences
import app.trecos.data.TrecosDatabase
import app.trecos.lock.AppLock
import app.trecos.lock.DeviceSecurity
import app.trecos.lock.SystemDeviceSecurity
import app.trecos.places.FieldStore
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

    /** Where the camera app writes captures; emptied after each import. */
    val cameraDir: java.io.File = java.io.File(context.cacheDir, "camera")

    /** A private scratch folder, such as for unpacking a backup before importing it. */
    val workDir: java.io.File = java.io.File(context.cacheDir, "work")

    /** For message texts outside screens. */
    val resources: android.content.res.Resources = context.resources

    /** Stored photos and thumbnails in app-private storage. */
    val photoStore: PhotoStore = PhotoStore(context.filesDir, context.contentResolver)

    /** Moving, copying, the trash and house deletion. */
    val organize: OrganizeStore by lazy { OrganizeStore(database, clock, newId) }

    /** Custom field definitions and values. */
    val fields: FieldStore by lazy { FieldStore(database, clock, newId) }

    /**
     * Deletes photo files nothing refers to any more, keeping the last hour's
     * (they may belong to a form that isn't saved yet).
     *
     * @return how many photos were deleted.
     */
    suspend fun freeUnusedPhotos(): Int =
        photoStore.deleteUnreferenced(database.photos().referencedHashes().toSet(), olderThan = clock() - 60 * 60 * 1000)

    /** The container "Search in this container" limits the Search tab to, or `null`. */
    val searchWithin = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Short messages shown app-wide, such as "Deleted" with Undo. */
    val messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)

    /** Which unfinished features are switched on (design D19); tests turn them on. */
    var features: Features = Features(driveSync = BuildConfig.FEATURE_DRIVE_SYNC, encryption = BuildConfig.FEATURE_ENCRYPTION)

    /** The phone's own lock; tests replace it with a fake. */
    var security: DeviceSecurity = SystemDeviceSecurity(context)

    /** Whether the app is locked right now. */
    val lock: AppLock = AppLock(clock)

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

/**
 * Feature flags (design D19).
 *
 * @property driveSync Google Drive sync (release 0.11).
 * @property encryption database encryption (release 0.12).
 */
data class Features(val driveSync: Boolean = false, val encryption: Boolean = false)
