package app.trecos.crypto

import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Copies the database into a new file with another key; tests fake it, since SQLCipher can't load on the JVM. */
fun interface DatabaseCipher {
    /**
     * @param source the database file, which the app may have open.
     * @param sourceKey the database file's key, or `null` when it is unencrypted.
     * @param target the new file.
     * @param key the new file's key, or `null` for an unencrypted file.
     * @param version the schema version to record in the new file.
     */
    fun export(source: File, sourceKey: ByteArray?, target: File, key: ByteArray?, version: Int)
}

/**
 * SQLCipher's `sqlcipher_export` (design D3, D16), on a connection of its own:
 * an ATTACH turns write-ahead logging off on the pool it runs on, which fails
 * while another thread is using the app's pool.
 */
object SqlCipherExport : DatabaseCipher {
    override fun export(source: File, sourceKey: ByteArray?, target: File, key: ByteArray?, version: Int) {
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        SQLiteDatabase.openDatabase(source.path, DatabaseKeys.passphrase(sourceKey), null, flags, null, null).use { db ->
            db.execSQL("ATTACH DATABASE ? AS next KEY ?", arrayOf(target.path, String(DatabaseKeys.passphrase(key))))
            try {
                db.rawQuery("SELECT sqlcipher_export('next')", null).use { it.moveToFirst() }
                // sqlcipher_export copies tables, indexes and triggers but not the schema version Room checks.
                db.execSQL("PRAGMA next.user_version = $version")
            } finally {
                db.execSQL("DETACH DATABASE next")
            }
        }
    }
}
