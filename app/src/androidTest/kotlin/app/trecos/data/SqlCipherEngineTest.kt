package app.trecos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks on a device that the database runs on SQLCipher's engine and that,
 * without a key, the file is plain SQLite (design D3).
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrecosDatabase

    @Before
    fun open() {
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
        db = TrecosDatabase.open(context)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
    }

    @Test
    fun runsOnSqlCipherWithoutAKey() = runBlocking {
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        assertEquals("Apartment", db.houses().get("h1")?.name)

        val version = db.openHelper.writableDatabase.query("PRAGMA cipher_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        assertTrue("cipher_version is $version", !version.isNullOrBlank())

        val header = context.getDatabasePath(TrecosDatabase.FILE_NAME).inputStream().use { it.readNBytes(15) }
        assertEquals("SQLite format 3", String(header))
    }
}
