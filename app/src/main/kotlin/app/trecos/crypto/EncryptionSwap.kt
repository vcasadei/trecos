package app.trecos.crypto

import java.io.File

/** How a start of the app finished an earlier re-encryption. */
enum class SwapOutcome {
    /** Nothing was pending. */
    Nothing,

    /** The prepared database was swapped in. */
    Swapped,

    /** An unfinished preparation was abandoned; the old database is untouched. */
    RolledBack,
}

/**
 * Re-encrypts the database in two halves (design D16) so that an interruption
 * at any point leaves a usable database:
 *
 * 1. [prepare], while the app runs: the new file is written next to the old
 *    one and its key staged. An interruption here leaves the old file in use.
 * 2. [finish], when the app starts and nothing has the database open: the new
 *    file replaces the old one, then the staged key becomes the current one.
 *
 * A marker file records the phase, so [finish] knows whether to complete or
 * abandon the work.
 *
 * @param dbFile the database file.
 * @param dir a private folder for the marker.
 * @param keys the database keys.
 */
class EncryptionSwap(private val dbFile: File, private val dir: File, private val keys: DatabaseKeys) {
    private val marker = File(dir, "swap.phase")

    /** The prepared database, next to the current one. */
    val staged: File = File(dbFile.parentFile, dbFile.name + ".next")

    private val previous = File(dbFile.parentFile, dbFile.name + ".previous")

    /**
     * Prepares the next database.
     *
     * @param key the key of the next database, or `null` for an unencrypted one.
     * @param export writes the current data, encrypted with [key], into the given file.
     */
    fun prepare(key: ByteArray?, export: (File) -> Unit) {
        dir.mkdirs()
        phase(EXPORTING)
        staged.delete()
        export(staged)
        keys.stage(key)
        phase(READY)
    }

    /**
     * Finishes or abandons pending work. Call before opening the database.
     *
     * @return what happened.
     */
    fun finish(): SwapOutcome = when (marker.takeIf { it.exists() }?.readText()) {
        null -> SwapOutcome.Nothing
        EXPORTING -> {
            staged.delete()
            keys.discardStaged()
            marker.delete()
            SwapOutcome.RolledBack
        }
        READY -> {
            if (staged.exists()) {
                sidecars(dbFile).forEach(File::delete)
                previous.delete()
                if (dbFile.exists()) check(dbFile.renameTo(previous)) { "Can't move the old database aside" }
                check(staged.renameTo(dbFile)) { "Can't move the new database in" }
            }
            phase(SWAPPED)
            finish()
            SwapOutcome.Swapped
        }
        SWAPPED -> {
            keys.commitStaged()
            previous.delete()
            marker.delete()
            SwapOutcome.Swapped
        }
        else -> {
            marker.delete()
            SwapOutcome.Nothing
        }
    }

    /** @return whether a preparation is waiting for the next start. */
    fun isPending(): Boolean = marker.exists()

    /** Writes the phase atomically. */
    private fun phase(value: String) {
        val tmp = File(dir, "swap.phase.tmp")
        tmp.writeText(value)
        if (!tmp.renameTo(marker)) {
            marker.delete()
            tmp.renameTo(marker)
        }
    }

    /** @return a database file's journal files. */
    private fun sidecars(file: File) = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private companion object {
        const val EXPORTING = "exporting"
        const val READY = "ready"
        const val SWAPPED = "swapped"
    }
}
