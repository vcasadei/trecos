package app.trecos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.trecos.crypto.AndroidKeystoreWrapper
import app.trecos.crypto.DatabaseKeys
import app.trecos.crypto.EncryptionSwap
import app.trecos.crypto.SqlCipherExport
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real re-encryption on a device (tasks 13.2, 13.3, 13.6): SQLCipher's
 * export, the swap, opening with the Keystore-wrapped key, search, and back.
 */
@RunWith(AndroidJUnit4::class)
class EncryptionDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbFile get() = context.getDatabasePath(TrecosDatabase.FILE_NAME)
    private val dir by lazy { File(context.noBackupFilesDir, "crypto-test").apply { deleteRecursively(); mkdirs() } }
    private val keys by lazy { DatabaseKeys(dir, AndroidKeystoreWrapper) }
    private val swap by lazy { EncryptionSwap(dbFile, dir, keys) }

    @Before
    fun clean() {
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
    }

    @After
    fun tidy() {
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
        dir.deleteRecursively()
    }

    /** @return the first bytes of the database file. */
    private fun header() = dbFile.inputStream().use { String(it.readNBytes(15)) }

    @Test
    fun encryptAndDecryptKeepEverything() = runBlocking {
        var db = TrecosDatabase.open(context)
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        db.items().insert(Item(id = "i1", houseId = "h1", name = "Cabeça de impressão", createdAt = 1, updatedAt = 1))

        val key = DatabaseKeys.newKey()
        swap.prepare(key) { target -> SqlCipherExport.export(db.openHelper.writableDatabase, target, key, TrecosDatabase.VERSION) }
        db.close()
        swap.finish()

        assertNotEquals("the file is encrypted", "SQLite format 3", header())
        db = TrecosDatabase.open(context, DatabaseKeys.passphrase(keys.current()))
        assertEquals("Cabeça de impressão", db.items().get("i1")?.name)
        assertEquals(listOf("i1"), db.search().matches("cabeca*").map { it.refId })
        db.items().insert(Item(id = "i2", houseId = "h1", name = "Mouse", createdAt = 2, updatedAt = 2))
        assertEquals(listOf("i2"), db.search().matches("mouse*").map { it.refId })

        swap.prepare(null) { target -> SqlCipherExport.export(db.openHelper.writableDatabase, target, null, TrecosDatabase.VERSION) }
        db.close()
        swap.finish()

        assertEquals("SQLite format 3", header())
        assertTrue(keys.current() == null)
        db = TrecosDatabase.open(context)
        assertEquals(setOf("i1", "i2"), db.snapshots().items("h1").map { it.id }.toSet())
        db.close()
    }
}
