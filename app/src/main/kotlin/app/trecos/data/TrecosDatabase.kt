package app.trecos.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The app's database. It always runs on SQLCipher's SQLite (design D3),
 * without a key until encryption is turned on.
 */
@Database(
    entities = [
        House::class, Container::class, Item::class,
        CustomCategory::class, ItemCategory::class, Tag::class, ItemTag::class, TokenCategoryCount::class,
        TrashEntry::class, Photo::class, SearchEntry::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = SearchIndex.Migration4To5::class),
    ],
)
abstract class TrecosDatabase : RoomDatabase() {
    /** @return houses. */
    abstract fun houses(): HouseDao

    /** @return containers. */
    abstract fun containers(): ContainerDao

    /** @return items. */
    abstract fun items(): ItemDao

    /** @return QR code checks. */
    abstract fun qr(): QrDao

    /** @return custom categories and assignments. */
    abstract fun categories(): CategoryDao

    /** @return tags. */
    abstract fun tags(): TagDao

    /** @return full-text search. */
    abstract fun search(): SearchDao

    /** @return photo rows. */
    abstract fun photos(): PhotoDao

    /** @return moving, copying, trash and purge operations. */
    abstract fun organize(): OrganizeDao

    companion object {
        /** The database file name in the app's private storage. */
        const val FILE_NAME = "trecos.db"

        /** The schema version of this build; keep in step with [Database.version]. */
        const val VERSION = 5

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
                    .addCallback(SearchIndex.onCreate)
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
