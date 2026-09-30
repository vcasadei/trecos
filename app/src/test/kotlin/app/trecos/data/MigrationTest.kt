package app.trecos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration tests against the exported schemas in `app/schemas/`. Every schema
 * change adds a test here that creates the previous version, fills it, runs the
 * migration and validates the result against the next exported schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrecosDatabase::class.java)

    @Test
    fun theExportedSchemaMatchesTheCurrentVersion() {
        assertEquals(TrecosDatabase.VERSION, 1)
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO house (id, name, icon, createdAt, updatedAt) VALUES ('h1', 'Apartment', 'house', 1, 1)")
        }
        helper.runMigrationsAndValidate(DB, 1, true).use { db ->
            db.query("SELECT name FROM house").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Apartment", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
