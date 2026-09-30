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

        /**
         * Opens the database file on the SQLCipher engine, unencrypted.
         *
         * @param context any context of the app.
         * @return the open database.
         */
        fun open(context: Context): TrecosDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, TrecosDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(ByteArray(0)))
                .build()
        }
    }
}
