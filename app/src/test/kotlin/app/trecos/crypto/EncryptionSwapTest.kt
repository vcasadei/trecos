package app.trecos.crypto

import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The re-encryption state machine on plain files (task 13.3), with
 * interruptions before, during and after the swap; the key at rest; and the
 * Drive snapshot cipher (task 13.4).
 */
class EncryptionSwapTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val wrapper = AesKeyWrapper(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    private val db by lazy { folder.newFolder("databases").let { File(it, "trecos.db") }.apply { writeText("plain data") } }
    private val dir by lazy { folder.newFolder("crypto") }
    private val keys by lazy { DatabaseKeys(dir, wrapper) }
    private val swap by lazy { EncryptionSwap(db, dir, keys) }
    private val key = DatabaseKeys.newKey()

    /** A fake export: writes the "encrypted" data. */
    private val export: (File) -> Unit = { it.writeText("encrypted data") }

    @Test
    fun turningItOnAndBackOff() {
        swap.prepare(key, export)
        assertTrue(swap.isPending())
        assertEquals("plain data", db.readText())

        assertEquals(SwapOutcome.Swapped, swap.finish())
        assertEquals("encrypted data", db.readText())
        assertArrayEquals(key, keys.current())
        assertFalse(swap.isPending())

        swap.prepare(null) { it.writeText("plain again") }
        swap.finish()
        assertEquals("plain again", db.readText())
        assertNull(keys.current())
    }

    @Test
    fun nothingPending() {
        assertEquals(SwapOutcome.Nothing, swap.finish())
        assertEquals("plain data", db.readText())
    }

    @Test
    fun interruptedWhileExporting() {
        // The phone shuts down half-way through the export: the marker says "exporting".
        assertThrows(IllegalStateException::class.java) {
            swap.prepare(key) { target ->
                target.writeText("half")
                throw IllegalStateException("power lost")
            }
        }
        assertEquals(SwapOutcome.RolledBack, swap.finish())
        assertEquals("plain data", db.readText())
        assertNull(keys.current())
        assertFalse(swap.staged.exists())
        assertFalse(swap.isPending())
    }

    @Test
    fun interruptedAfterPreparingBeforeTheSwap() {
        swap.prepare(key, export)
        // The app is killed before it restarts; the next start swaps.
        assertEquals(SwapOutcome.Swapped, EncryptionSwap(db, dir, keys).finish())
        assertEquals("encrypted data", db.readText())
        assertArrayEquals(key, keys.current())
    }

    @Test
    fun interruptedBetweenTheTwoRenames() {
        swap.prepare(key, export)
        // The old file was moved aside, the new one not yet moved in.
        assertTrue(db.renameTo(File(db.parentFile, "trecos.db.previous")))
        assertEquals(SwapOutcome.Swapped, swap.finish())
        assertEquals("encrypted data", db.readText())
        assertArrayEquals(key, keys.current())
        assertFalse(File(db.parentFile, "trecos.db.previous").exists())
    }

    @Test
    fun interruptedAfterTheSwapBeforeTheKey() {
        swap.prepare(key, export)
        assertTrue(db.renameTo(File(db.parentFile, "trecos.db.previous")))
        assertTrue(swap.staged.renameTo(db))
        // The new file is in place but the key not yet committed.
        assertEquals(SwapOutcome.Swapped, swap.finish())
        assertArrayEquals(key, keys.current())
    }

    @Test
    fun theOldJournalIsNotAppliedToTheNewFile() {
        File(db.path + "-wal").writeText("old journal")
        swap.prepare(key, export)
        swap.finish()
        assertFalse(File(db.path + "-wal").exists())
    }

    @Test
    fun theKeyIsWrappedAtRest() {
        swap.prepare(key, export)
        swap.finish()
        val stored = dir.listFiles()!!.filter { it.name.startsWith("key") }.map { it.readBytes() }
        assertTrue(stored.isNotEmpty())
        stored.forEach { bytes -> assertFalse(bytes.toList().windowed(key.size).any { it == key.toList() }) }
    }

    @Test
    fun passphraseIsTheHexKey() {
        assertEquals("00ff10", String(DatabaseKeys.passphrase(byteArrayOf(0, -1, 16))))
        assertEquals(0, DatabaseKeys.passphrase(null).size)
        assertEquals(32, DatabaseKeys.newKey().size)
    }

    @Test
    fun stagedKeysCanBeDiscarded() {
        keys.stage(key)
        assertArrayEquals(key, keys.staged())
        keys.discardStaged()
        assertNull(keys.staged())
        keys.stage(null)
        assertNull(keys.staged())
        keys.commitStaged()
        assertNull(keys.current())
    }

    @Test
    fun driveCopiesAreUnreadableWithoutTheKey() {
        val plain = "{\"t\":\"item\",\"r\":{\"name\":\"Raspberry Pi\"}}".toByteArray()
        val sealed = SnapshotCipher.encrypt(plain, key)

        assertTrue(SnapshotCipher.isEncrypted(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("Raspberry"))
        assertArrayEquals(plain, SnapshotCipher.decrypt(sealed, key))
        assertThrows(MissingKeyException::class.java) { SnapshotCipher.decrypt(sealed, null) }
        assertThrows(Exception::class.java) { SnapshotCipher.decrypt(sealed, DatabaseKeys.newKey()) }
        assertArrayEquals("plain".toByteArray(), SnapshotCipher.decrypt("plain".toByteArray(), null))
    }
}
