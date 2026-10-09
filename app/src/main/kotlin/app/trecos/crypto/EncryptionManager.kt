package app.trecos.crypto

import android.content.Context
import android.content.Intent
import app.trecos.data.TrecosDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What the encryption setting shows.
 *
 * @property on whether the database is encrypted.
 * @property working whether it is being encrypted or decrypted now.
 * @property pending whether a change waits for the next start of the app.
 */
data class EncryptionStatus(val on: Boolean, val working: Boolean = false, val pending: Boolean = false)

/**
 * Optional database encryption (encryption spec, design D16): the key is
 * wrapped by the Android Keystore on the device and kept in Drive's hidden
 * app folder for recovery; the database is re-encrypted with SQLCipher and
 * swapped in at the next start; Drive snapshots are encrypted with the same key.
 *
 * @param context the application context.
 * @param database the open database.
 */
class EncryptionManager(private val context: Context, private val database: () -> TrecosDatabase) {
    /** Protects the key on the device; tests use a plain AES key. */
    var wrapper: KeyWrapper = AndroidKeystoreWrapper

    /** Re-encrypts the database; tests fake it, since SQLCipher can't load on the JVM. */
    var cipher: DatabaseCipher = SqlCipherExport

    /** Restarts the app to finish a re-encryption; tests record the call instead. */
    var restart: () -> Unit = {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }

    private val dir = File(context.noBackupFilesDir, "crypto")
    private val retired = File(dir, "key.retired")

    /** The database keys on this device. */
    val keys: DatabaseKeys get() = DatabaseKeys(dir, wrapper)

    /** The re-encryption state machine. */
    val swap: EncryptionSwap get() = EncryptionSwap(context.getDatabasePath(TrecosDatabase.FILE_NAME), dir, keys)

    private var sessionKey: ByteArray? = null
    private val statusState = MutableStateFlow(EncryptionStatus(on = false))

    /** The current status. */
    val status: StateFlow<EncryptionStatus> = statusState.asStateFlow()

    /** Re-reads the status. */
    fun refresh() {
        statusState.value = EncryptionStatus(on = keys.isEncrypted(), working = statusState.value.working, pending = swap.isPending())
    }

    /**
     * The key Drive snapshots are encrypted with, or `null` when encryption is off.
     *
     * @return the key.
     */
    fun snapshotKey(): ByteArray? = runCatching { keys.staged() ?: keys.current() }.getOrNull() ?: sessionKey

    /**
     * Every key this device may need to read older Drive snapshots.
     *
     * @return the keys, newest first.
     */
    fun readKeys(): List<ByteArray> = listOfNotNull(
        snapshotKey(),
        runCatching { retired.takeIf { it.exists() }?.readBytes()?.let(wrapper::unwrap) }.getOrNull(),
    )

    /**
     * @return the account's key when Drive has one and this device's database isn't encrypted with it yet.
     */
    fun accountKeyOnly(): ByteArray? = sessionKey?.takeIf { !keys.isEncrypted() && !swap.isPending() }

    /**
     * Uses a key found in Drive until this device's database is encrypted with it.
     *
     * @param key the key from the vault.
     */
    fun useSessionKey(key: ByteArray) {
        sessionKey = key
    }

    /**
     * Turns encryption on: stores the key in the vault first (so it can always
     * be recovered), then prepares the encrypted database and restarts the app
     * to swap it in. On failure nothing changes locally.
     *
     * @param vault where the key is kept for recovery.
     * @param existing a key found in the vault (recovery, or another device turned it on), or `null` for a new one.
     * @param restartNow whether to restart at once; otherwise the next start finishes it.
     * @return `true` when it succeeded.
     */
    suspend fun enable(vault: KeyVault?, existing: ByteArray? = null, restartNow: Boolean = true): Boolean = work {
        val key = existing ?: DatabaseKeys.newKey()
        if (existing == null) vault?.put(key) ?: error("A connected Google account is required")
        prepare(key)
        if (restartNow) restart()
    }

    /**
     * Turns encryption off: prepares the decrypted database, removes the key
     * from the vault and restarts. The old key is kept on the device only to
     * read older Drive snapshots.
     *
     * @param vault where the key is kept.
     * @param restartNow whether to restart at once.
     * @return `true` when it succeeded.
     */
    suspend fun disable(vault: KeyVault?, restartNow: Boolean = true): Boolean = work {
        val old = keys.current() ?: return@work
        prepare(null)
        dir.mkdirs()
        retired.writeBytes(wrapper.wrap(old))
        vault?.delete()
        if (restartNow) restart()
    }

    /** Writes the next database; an interruption leaves the current one in use. */
    private suspend fun prepare(key: ByteArray?) = withContext(Dispatchers.IO) {
        try {
            swap.prepare(key) { target -> cipher.export(database().openHelper.writableDatabase, target, key, TrecosDatabase.VERSION) }
        } catch (e: Exception) {
            if (swap.isPending()) swap.finish()
            throw e
        }
    }

    /** Runs a change with the working flag, turning failures into `false`. */
    private suspend fun work(block: suspend () -> Unit): Boolean {
        statusState.value = statusState.value.copy(working = true)
        return try {
            block()
            true
        } catch (e: Exception) {
            false
        } finally {
            statusState.value = statusState.value.copy(working = false)
            refresh()
        }
    }
}
