package app.trecos

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs the device tests in [DeviceTestApplication]: they open, delete and
 * re-create the database file themselves, and the app's own start-up work
 * must not open it at the same time (an Android 10 cell failed with a disk
 * I/O error when it did).
 */
class DeviceTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, DeviceTestApplication::class.java.name, context)
}

/** The application without its background maintenance and sync scheduling. */
class DeviceTestApplication : TrecosApplication() {
    override fun scheduleMaintenance() = Unit
}
