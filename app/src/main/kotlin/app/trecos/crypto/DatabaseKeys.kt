package app.trecos.crypto

import java.io.File
import java.security.SecureRandom

/**
 * The database key on this device, wrapped by [KeyWrapper]: `key.bin` is the
 * key in use; `key.next` is staged while the database is being re-encrypted,
 * and holds [PLAIN] when the next database is unencrypted.
 *
 * @param dir a private folder that isn't backed up.
 * @param wrapper protects the key at rest.
 */
class DatabaseKeys(private val dir: File, private val wrapper: KeyWrapper) {
    private val current = File(dir, "key.bin")
    private val next = File(dir, "key.next")

    /** @return the key in use, or `null` when the database isn't encrypted. */
    fun current(): ByteArray? = current.takeIf { it.exists() }?.let { wrapper.unwrap(it.readBytes()) }

    /** @return whether the database is encrypted. */
    fun isEncrypted(): Boolean = current.exists()

    /** @return the staged key, `null` when none is staged or the next database is unencrypted. */
    fun staged(): ByteArray? = next.takeIf { it.exists() }?.readBytes()?.takeIf { !it.contentEquals(PLAIN) }?.let(wrapper::unwrap)

    /**
     * Stages the key of the database being prepared.
     *
     * @param key the key, or `null` for an unencrypted database.
     */
    fun stage(key: ByteArray?) {
        dir.mkdirs()
        val tmp = File(dir, "key.next.tmp")
        tmp.writeBytes(key?.let(wrapper::wrap) ?: PLAIN)
        if (!tmp.renameTo(next)) {
            next.delete()
            tmp.renameTo(next)
        }
    }

    /** Makes the staged key the one in use; called when the prepared database is swapped in. */
    fun commitStaged() {
        if (!next.exists()) return
        if (next.readBytes().contentEquals(PLAIN)) {
            current.delete()
            next.delete()
        } else {
            current.delete()
            next.renameTo(current)
        }
    }

    /** Forgets the staged key; called when a preparation is abandoned. */
    fun discardStaged() {
        next.delete()
    }

    companion object {
        /** Marks a staged unencrypted database. */
        val PLAIN = "PLAIN".toByteArray()

        /** @return a new random 256-bit key. */
        fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

        /**
         * @param key a database key.
         * @return the passphrase SQLCipher opens the database with: the key in hex.
         */
        fun passphrase(key: ByteArray?): ByteArray = key?.joinToString("") { "%02x".format(it) }?.toByteArray() ?: ByteArray(0)
    }
}
