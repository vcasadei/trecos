package app.trecos.data

import android.util.Log
import java.io.File

/**
 * Raised when a migration failed and the database was put back as it was.
 *
 * @param from the schema version before the attempt.
 * @param to the schema version the app expected.
 * @param cause what failed.
 */
class MigrationFailedException(val from: Int, val to: Int, cause: Throwable) :
    RuntimeException("Migration from schema $from to $to failed; the database was restored to schema $from", cause)

/**
 * The "down" path of the migration plan: Android can't install an older app
 * over a newer one, so rollback is data-level. Before any migration, the
 * database file is copied to `pre-migration-v<N>.db`; a failed migration puts
 * the copy back, and the next launch that opens without migrating deletes it.
 *
 * @param dbFile the database file.
 * @param targetVersion the schema version this build of the app expects.
 * @param readVersion reads a database file's schema version (`PRAGMA user_version`).
 */
class MigrationGuard(
    private val dbFile: File,
    private val targetVersion: Int,
    private val readVersion: (File) -> Int,
) {
    /**
     * @param version a schema version.
     * @return the safety copy's file for that version.
     */
    fun backupFile(version: Int): File = File(dbFile.parentFile, "pre-migration-v$version.db")

    /**
     * Opens the database through [openAndMigrate], which must run any pending
     * migration before returning.
     *
     * @param openAndMigrate opens the database and forces its migrations.
     * @return what [openAndMigrate] returned.
     * @throws MigrationFailedException if a migration failed; the old file is back in place.
     */
    fun <T> open(openAndMigrate: () -> T): T {
        val current = if (dbFile.exists()) readVersion(dbFile) else 0
        val migrating = current in 1 until targetVersion
        if (migrating) {
            dbFile.copyTo(backupFile(current), overwrite = true)
        } else {
            deleteBackups()
        }
        return try {
            openAndMigrate()
        } catch (failure: Exception) {
            if (!migrating) throw failure
            restore(current)
            Log.w(TAG, "Migration $current -> $targetVersion failed; restored the previous database")
            throw MigrationFailedException(current, targetVersion, failure)
        }
    }

    /**
     * Puts a safety copy back in place of the database file. Callers close the
     * database first.
     *
     * @param version the schema version of the copy to restore.
     * @return `true` if a copy existed and was restored.
     */
    fun restore(version: Int): Boolean {
        val backup = backupFile(version)
        if (!backup.exists()) return false
        listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        backup.copyTo(dbFile, overwrite = true)
        return true
    }

    /** Deletes every safety copy; called when the database opened without migrating. */
    fun deleteBackups() {
        dbFile.parentFile?.listFiles { file -> file.name.matches(BACKUP_NAME) }?.forEach(File::delete)
    }

    private companion object {
        const val TAG = "MigrationGuard"
        val BACKUP_NAME = Regex("""pre-migration-v\d+\.db""")
    }
}
