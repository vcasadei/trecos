package app.trecos

import android.app.Application

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
    }

    /**
     * Builds the container; tests override this to use an in-memory database.
     *
     * @return the container for this process.
     */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
