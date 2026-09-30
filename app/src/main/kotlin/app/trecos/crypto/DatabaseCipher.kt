package app.trecos.crypto

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** Copies the open database into a new file with another key; tests fake it, since SQLCipher can't load on the JVM. */
fun interface DatabaseCipher {
    /**
     * @param db the open database.
     * @param target the new file.
     * @param key the new file's key, or `null` for an unencrypted file.
     * @param version the schema version to record in the new file.
     */
    fun export(db: SupportSQLiteDatabase, target: File, key: ByteArray?, version: Int)
}

/** SQLCipher's `sqlcipher_export` (design D3, D16). */
object SqlCipherExport : DatabaseCipher {
    override fun export(db: SupportSQLiteDatabase, target: File, key: ByteArray?, version: Int) {
        val passphrase = String(DatabaseKeys.passphrase(key))
        db.execSQL("ATTACH DATABASE ? AS next KEY ?", arrayOf(target.path, passphrase))
        try {
            db.query("SELECT sqlcipher_export('next')").use { it.moveToFirst() }
            // sqlcipher_export copies tables, indexes and triggers but not the schema version Room checks.
            db.execSQL("PRAGMA next.user_version = $version")
        } finally {
            db.execSQL("DETACH DATABASE next")
        }
    }
}
