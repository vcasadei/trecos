package app.trecos.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DAO tests on an in-memory database. The SQLCipher engine only loads on
 * Android; `SqlCipherEngineTest` in `androidTest` covers it on the emulator.
 */
@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrecosDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = db.close()

    @Test
    fun housesExcludeDeletedAndSortByName() = runBlocking {
        db.houses().insert(house("h1", "parents"))
        db.houses().insert(house("h2", "Apartment"))
        db.houses().insert(house("h3", "Old").copy(deletedAt = Fixtures.NOW))

        assertEquals(listOf("Apartment", "parents"), db.houses().observeAll().first().map { it.name })
        assertEquals(2, db.houses().count())
        assertNull(db.houses().get("h3"))
    }

    @Test
    fun childrenAreFilteredByLevel() = runBlocking {
        db.houses().insert(house())
        db.containers().insert(container("office"))
        db.containers().insert(container("boxA", parentId = "office"))
        db.containers().insert(container("gone", parentId = "office").copy(deletedAt = Fixtures.NOW))
        db.items().insert(item("laptop", containerId = "office"))
        db.items().insert(item("keys"))

        assertEquals(listOf("office"), db.containers().observeChildren("h1", null).first().map { it.id })
        assertEquals(listOf("boxA"), db.containers().observeChildren("h1", "office").first().map { it.id })
        assertEquals(listOf("keys"), db.items().observeIn("h1", null).first().map { it.id })
        assertEquals(listOf("laptop"), db.items().observeIn("h1", "office").first().map { it.id })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun qrCodeIsUniqueWithinAHouse() = runBlocking {
        db.items().insert(item("a").copy(qrCode = "Armario#1"))
        db.items().insert(item("b").copy(qrCode = "Armario#1"))
    }

    @Test
    fun sameQrCodeIsAllowedInAnotherHouseAndManyItemsMayHaveNone() = runBlocking {
        db.items().insert(item("a").copy(qrCode = "Armario#1"))
        db.items().insert(item("b", houseId = "h2").copy(qrCode = "Armario#1"))
        db.items().insert(item("c"))
        db.items().insert(item("d"))
        assertEquals(listOf("a", "c", "d"), db.items().observeIn("h1", null).first().map { it.id })
    }

    @Test
    fun updateKeepsTheAddedDateAndChangesFields() = runBlocking {
        db.items().insert(item("a"))
        db.items().update(item("a").copy(quantity = 5, updatedAt = Fixtures.NOW + 1))
        val saved = db.items().get("a")!!
        assertEquals(5, saved.quantity)
        assertEquals(Fixtures.NOW, saved.createdAt)
    }

    @Test
    fun qrUsesSpanItemsAndContainers() = runBlocking {
        db.containers().insert(container("wardrobe").copy(qrCode = "Armario#1"))
        assertEquals(1, db.qr().countUses("h1", "Armario#1", exceptId = "new"))
        assertEquals(0, db.qr().countUses("h1", "Armario#1", exceptId = "wardrobe"))
        assertEquals(0, db.qr().countUses("h2", "Armario#1", exceptId = "new"))
    }

    @Test
    fun totalsSumPricedItemsAndCountUnpriced() = runBlocking {
        db.items().insert(item("screws", containerId = "boxA", quantity = 200, unitPrice = 10))
        db.items().insert(item("cable", containerId = "boxA", quantity = 2, unitPrice = 1_500))
        db.items().insert(item("mystery", containerId = "boxA", quantity = 3))
        db.items().insert(item("keys", quantity = 1))

        val totals = db.items().observeTotals("h1").first().associateBy { it.containerId }
        assertEquals(ContainerTotal("boxA", total = 2_000 + 3_000, unpriced = 1, items = 3), totals["boxA"])
        assertEquals(ContainerTotal(null, total = 0, unpriced = 1, items = 1), totals[null])
    }
}
