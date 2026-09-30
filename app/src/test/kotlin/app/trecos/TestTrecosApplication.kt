package app.trecos

import androidx.room.Room
import app.trecos.data.TrecosDatabase

/**
 * The application used by every Robolectric test (`robolectric.properties`):
 * the same container, but with an in-memory database, because SQLCipher's
 * native engine can't load on the JVM.
 */
class TestTrecosApplication : TrecosApplication() {

    /**
     * @return a container whose database lives in memory.
     */
    override fun createContainer(): AppContainer = AppContainer(
        context = this,
        openDatabase = {
            Room.inMemoryDatabaseBuilder(this, TrecosDatabase::class.java).allowMainThreadQueries().build()
        },
    )
}
