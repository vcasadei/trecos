package app.trecos

import android.app.Application
import app.trecos.places.PurgeWorker
import app.trecos.sync.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The application: creates the [AppContainer] once per process.
 */
open class TrecosApplication : Application() {

    /** The app's long-lived objects. */
    lateinit var container: AppContainer
        private set

    /** Creates the container before any screen asks for it. */
    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        scheduleMaintenance()
    }

    /** Schedules background maintenance (the daily trash purge) and, when connected, sync; tests turn it off. */
    protected open fun scheduleMaintenance() {
        PurgeWorker.schedule(this)
        if (container.features.driveSync) {
            container.scope.launch {
                if (container.sync.store.load().connected) SyncWorker.schedule(this@TrecosApplication, container.preferences.syncFrequency.first())
            }
        }
    }

    /**
     * Builds the container; tests override this to use an in-memory database.
     *
     * @return the container for this process.
     */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
