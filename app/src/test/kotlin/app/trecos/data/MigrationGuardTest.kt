package app.trecos.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The migration "down" path: the pre-migration copy (task 3.1) and restoring
 * it (task 3.4). Runs on the platform SQLite; the guard only handles files.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationGuardTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name = "guard-test.db"
    private val file: File = context.getDatabasePath(name)

    /** Reads a database file's `user_version`. */
    private val readVersion: (File) -> Int = { path ->
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }

    /** The app's current schema version, which the tests migrate from. */
    private val current = TrecosDatabase.VERSION

    /** The version the sample migration moves to. */
    private val next = current + 1

    /** Creates a current-version database with one house and three items, then closes it. */
    private fun createCurrent() {
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, TrecosDatabase::class.java, name).allowMainThreadQueries().build()
        runBlocking {
            db.houses().insert(house())
            (1..3).forEach { db.items().insert(item("i$it")) }
        }
        db.close()
    }

    /**
     * @return the number of items, and whether the item table has a column named `extra`.
     */
    private fun inspect(): Pair<Int, Boolean> = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val count = db.rawQuery("SELECT COUNT(*) FROM item", null).use { it.moveToFirst(); it.getInt(0) }
        val columns = db.rawQuery("PRAGMA table_info(item)", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) } }
        count to ("extra" in columns)
    }

    /**
     * Applies a sample change to the next version directly to the file: adds a column,
     * deletes rows, and bumps `user_version`.
     *
     * @param thenFail whether to throw after changing the file, like a migration that breaks halfway.
     */
    private fun sampleMigration(thenFail: Boolean) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE item ADD COLUMN extra TEXT")
            db.execSQL("DELETE FROM item")
            db.version = next
        }
        if (thenFail) error("sample migration failed halfway")
    }

    @Test
    fun aFailingMigrationLeavesTheDatabaseReadableAtThePreviousVersion() {
        createCurrent()
        val guard = MigrationGuard(file, targetVersion = next, readVersion = readVersion)

        try {
            guard.open { sampleMigration(thenFail = true) }
            fail("the failure must be reported")
        } catch (expected: MigrationFailedException) {
            assertEquals(current, expected.from)
            assertEquals(next, expected.to)
        }

        assertEquals(current, readVersion(file))
        assertEquals(3 to false, inspect())
        val reopened = Room.databaseBuilder(context, TrecosDatabase::class.java, name).allowMainThreadQueries().build()
        assertEquals("Apartment", runBlocking { reopened.houses().get("h1")?.name })
        reopened.close()
    }

    @Test
    fun theDownPathRestoresThePriorSchemaAndRowCounts() {
        createCurrent()
        val guard = MigrationGuard(file, targetVersion = next, readVersion = readVersion)
        guard.open { sampleMigration(thenFail = false) }
        assertEquals(next, readVersion(file))
        assertEquals(0 to true, inspect())

        assertTrue(guard.restore(current))

        assertEquals(current, readVersion(file))
        assertEquals(3 to false, inspect())
    }

    @Test
    fun theCopyIsKeptUntilTheNextCleanLaunch() {
        createCurrent()
        MigrationGuard(file, targetVersion = next, readVersion = readVersion).open { sampleMigration(thenFail = false) }
        assertTrue(File(file.parentFile, "pre-migration-v$current.db").exists())

        MigrationGuard(file, targetVersion = next, readVersion = readVersion).open { }

        assertFalse(File(file.parentFile, "pre-migration-v$current.db").exists())
    }

    @Test
    fun aNewInstallMakesNoCopy() {
        context.deleteDatabase(name)
        MigrationGuard(file, targetVersion = 1, readVersion = readVersion).open { }
        assertFalse(File(file.parentFile, "pre-migration-v0.db").exists())
    }

    @Test
    fun otherFailuresAreNotTreatedAsMigrations() {
        createCurrent()
        try {
            MigrationGuard(file, targetVersion = current, readVersion = readVersion).open { error("unrelated") }
            fail("the failure must pass through")
        } catch (expected: IllegalStateException) {
            assertEquals("unrelated", expected.message)
        }
        assertFalse(guardHasBackup())
    }

    /** @return whether any safety copy exists. */
    private fun guardHasBackup() = file.parentFile!!.listFiles()!!.any { it.name.startsWith("pre-migration-") }
}
