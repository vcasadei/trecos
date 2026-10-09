package app.trecos.crypto

import javax.crypto.spec.SecretKeySpec

/** A Drive file is encrypted and this device has no key for it. */
class MissingKeyException : Exception("This data is encrypted and the key isn't on this device")

/**
 * Encrypts Drive snapshots with AES-GCM and the database key (design D16).
 * Encrypted files start with [MAGIC]; photos are never encrypted.
 */
object SnapshotCipher {
    /** The first bytes of an encrypted file. */
    val MAGIC = "TRCE1".toByteArray()

    /**
     * @param bytes the file.
     * @param key the database key.
     * @return the encrypted file.
     */
    fun encrypt(bytes: ByteArray, key: ByteArray): ByteArray = MAGIC + AesGcm.encrypt(SecretKeySpec(key, "AES"), bytes)

    /**
     * @param bytes a file, encrypted or not.
     * @return whether it is encrypted.
     */
    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /**
     * @param bytes a file, encrypted or not.
     * @param key the database key, or `null` when this device has none.
     * @return the plain file.
     * @throws MissingKeyException when it is encrypted and there is no key.
     */
    fun decrypt(bytes: ByteArray, key: ByteArray?): ByteArray {
        if (!isEncrypted(bytes)) return bytes
        key ?: throw MissingKeyException()
        return AesGcm.decrypt(SecretKeySpec(key, "AES"), bytes.copyOfRange(MAGIC.size, bytes.size))
    }
}
