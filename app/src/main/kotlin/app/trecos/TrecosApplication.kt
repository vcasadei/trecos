package app.trecos

import android.app.Application
import app.trecos.places.PurgeWorker

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

    /** Schedules background maintenance (the daily trash purge); tests turn it off. */
    protected open fun scheduleMaintenance() = PurgeWorker.schedule(this)

    /**
     * Builds the container; tests override this to use an in-memory database.
     *
     * @return the container for this process.
     */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
