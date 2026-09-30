package app.trecos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.trecos.data.AppPreferences
import app.trecos.data.TrecosDatabase
import java.util.UUID

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

    /** Device preferences. */
    val preferences: AppPreferences = AppPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
    )
}
