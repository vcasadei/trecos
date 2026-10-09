package app.trecos.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects the database key at rest on this device (design D16). */
interface KeyWrapper {
    /**
     * @param key the database key.
     * @return it encrypted for storage on this device.
     */
    fun wrap(key: ByteArray): ByteArray

    /**
     * @param blob a result of [wrap].
     * @return the database key.
     */
    fun unwrap(blob: ByteArray): ByteArray
}

/**
 * Wraps with an AES-GCM key that never leaves the Android Keystore. Robolectric
 * has no Keystore, so tests use a plain [AesKeyWrapper].
 */
object AndroidKeystoreWrapper : KeyWrapper {
    private const val ALIAS = "trecos-database-key-wrap"

    /** @return the Keystore key, created the first time. */
    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun wrap(key: ByteArray): ByteArray = AesGcm.encrypt(keystoreKey(), key, randomIv = false)

    override fun unwrap(blob: ByteArray): ByteArray = AesGcm.decrypt(keystoreKey(), blob)
}

/**
 * Wraps with a given AES key, for tests.
 *
 * @param key the wrapping key.
 */
class AesKeyWrapper(private val key: SecretKey) : KeyWrapper {
    override fun wrap(key: ByteArray): ByteArray = AesGcm.encrypt(this.key, key, randomIv = true)
    override fun unwrap(blob: ByteArray): ByteArray = AesGcm.decrypt(key, blob)
}

/** AES-GCM with the IV stored in front of the ciphertext. */
object AesGcm {
    private const val IV = 12
    private const val TAG_BITS = 128

    /**
     * @param key the AES key.
     * @param plain the data.
     * @param randomIv whether to pick the IV here; Keystore keys pick their own.
     * @return IV followed by ciphertext and tag.
     */
    fun encrypt(key: SecretKey, plain: ByteArray, randomIv: Boolean = true): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (randomIv) {
            val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        }
        return cipher.iv + cipher.doFinal(plain)
    }

    /**
     * @param key the AES key.
     * @param blob a result of [encrypt].
     * @return the data.
     * @throws javax.crypto.AEADBadTagException when the data was altered or the key is wrong.
     */
    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV))
        return cipher.doFinal(blob, IV, blob.size - IV)
    }
}
