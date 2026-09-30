package app.trecos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The app's database. It always runs on SQLCipher's SQLite (design D3),
 * without a key until encryption is turned on.
 */
@Database(entities = [House::class, Container::class, Item::class], version = 1, exportSchema = true)
abstract class TrecosDatabase : RoomDatabase() {
    /** @return houses. */
    abstract fun houses(): HouseDao

    /** @return containers. */
    abstract fun containers(): ContainerDao

    /** @return items. */
    abstract fun items(): ItemDao

    /** @return QR code checks. */
    abstract fun qr(): QrDao

    companion object {
        /** The database file name in the app's private storage. */
        const val FILE_NAME = "trecos.db"

        /** The schema version of this build; keep in step with [Database.version]. */
        const val VERSION = 1

        /**
         * Opens the database file on the SQLCipher engine, unencrypted, running
         * any pending migration behind a safety copy ([MigrationGuard]).
         *
         * @param context any context of the app.
         * @return the open, migrated database.
         * @throws MigrationFailedException if a migration failed; the previous database is back.
         */
        fun open(context: Context): TrecosDatabase {
            System.loadLibrary("sqlcipher")
            val file = context.getDatabasePath(FILE_NAME)
            val guard = MigrationGuard(file, VERSION) { path ->
                net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                    path.path, "", null, net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY, null,
                ).use { it.version }
            }
            return guard.open {
                val db = Room.databaseBuilder(context.applicationContext, TrecosDatabase::class.java, FILE_NAME)
                    .openHelperFactory(SupportOpenHelperFactory(ByteArray(0)))
                    .build()
                try {
                    db.openHelper.writableDatabase
                    db
                } catch (failure: Exception) {
                    db.close()
                    throw failure
                }
            }
        }
    }
}
