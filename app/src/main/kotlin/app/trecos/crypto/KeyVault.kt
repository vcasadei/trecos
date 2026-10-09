package app.trecos.crypto

import app.trecos.sync.DriveFiles

/** Where the database key is kept for recovery on a new phone (design D16). */
interface KeyVault {
    /** @return the key, or `null` when none is stored. */
    suspend fun get(): ByteArray?

    /** @param key the key to store, replacing any other. */
    suspend fun put(key: ByteArray)

    /** Removes the stored key. */
    suspend fun delete()
}

/**
 * The key in Drive's hidden app folder (`appDataFolder`, `drive.appdata`
 * scope): invisible in Drive and readable only by Trecos with the same account.
 *
 * @param drive the Drive API.
 */
class DriveKeyVault(private val drive: DriveFiles) : KeyVault {
    override suspend fun get(): ByteArray? = file()?.let { drive.download(it) }

    override suspend fun put(key: ByteArray) {
        val existing = file()
        if (existing != null) drive.update(existing, key) else drive.upload(NAME, "appDataFolder", key)
    }

    override suspend fun delete() {
        drive.list("name = '$NAME'", space = SPACE).forEach { drive.delete(it.id) }
    }

    /** @return the key file's id, or `null`. */
    private suspend fun file(): String? = drive.list("name = '$NAME'", space = SPACE).firstOrNull()?.id

    private companion object {
        const val NAME = "trecos-key.bin"
        const val SPACE = "appDataFolder"
    }
}
