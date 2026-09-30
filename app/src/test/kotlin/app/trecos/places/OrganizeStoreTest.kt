package app.trecos.places

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.ContainerTotal
import app.trecos.data.CustomCategory
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.data.ItemCategory
import app.trecos.data.ItemTag
import app.trecos.data.Tag
import app.trecos.data.TrecosDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The organize and trash rules at the data level (tasks 5.1-5.3, 5.6-5.8, 5.10).
 */
@RunWith(RobolectricTestRunner::class)
class OrganizeStoreTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrecosDatabase::class.java)
        .allowMainThreadQueries().build()
    private var now = 2_000_000_000_000L
    private var ids = 0
    private val store = OrganizeStore(db, clock = { now }, newId = { "n${ids++}" })

    @Before
    fun seed() = runBlocking {
        db.houses().insert(house("h1", "Apartment"))
        db.houses().insert(house("h2", "Parents"))
        db.containers().insert(container("boxA", name = "Box A").copy(qrCode = "BOX-A"))
        db.containers().insert(container("cables", parentId = "boxA", name = "Cables bag"))
        db.containers().insert(container("drawer", name = "Office drawer"))
        db.items().insert(item("pi", containerId = "boxA", unitPrice = 45_000).copy(name = "Raspberry Pi 4"))
        db.items().insert(item("usb", containerId = "cables", unitPrice = 1_500).copy(name = "USB-C cable"))
    }

    @After
    fun close() = db.close()

    /** @return the items directly in a container of h1. */
    private fun itemsIn(containerId: String?, house: String = "h1") = runBlocking { db.items().observeIn(house, containerId).first().map { it.id } }

    /** @return the containers directly in a container of h1. */
    private fun containersIn(parentId: String?, house: String = "h1") = runBlocking { db.containers().observeChildren(house, parentId).first().map { it.id } }

    @Test
    fun movingAnItem() = runBlocking {
        now += 1_000
        store.move(Selection(itemIds = listOf("pi")), Destination("h1", "drawer"))

        assertEquals(listOf("pi"), itemsIn("drawer"))
        assertEquals(now, db.items().get("pi")!!.updatedAt)
    }

    @Test
    fun movingAContainerIntoItsOwnChildIsRefused() = runBlocking {
        assertTrue(store.isInsideSelection(Selection(containerIds = listOf("boxA")), Destination("h1", "cables")))
        assertTrue(store.isInsideSelection(Selection(containerIds = listOf("boxA")), Destination("h1", "boxA")))
        assertFalse(store.isInsideSelection(Selection(containerIds = listOf("boxA")), Destination("h1", "drawer")))
        try {
            store.move(Selection(containerIds = listOf("boxA")), Destination("h1", "cables"))
            error("must be refused")
        } catch (expected: MoveIntoItselfException) {
            assertEquals(null, db.containers().get("boxA")!!.parentId)
        }
    }

    @Test
    fun movingAContainerMovesEverythingInside() = runBlocking {
        store.move(Selection(containerIds = listOf("boxA")), Destination("h1", "drawer"))
        assertEquals(listOf("boxA"), containersIn("drawer"))
        assertEquals(listOf("cables"), containersIn("boxA"))
        assertEquals(listOf("usb"), itemsIn("cables"))
    }

    @Test
    fun carryingCustomCategoriesAlong() = runBlocking {
        db.categories().insertCustom(CustomCategory("pt", "h2", "tools", "Power tools", null, 1, 1))
        db.tags().insert(Tag("t1", "h2", "borrowed", "borrowed", 1, 1))
        db.items().insert(item("drill", houseId = "h2").copy(name = "Drill"))
        db.categories().insertAssignments(listOf(ItemCategory("a", "h2", "drill", "pt", 0, 1), ItemCategory("b", "h2", "drill", "tools.power", 1, 1)))
        db.tags().insertAssignments(listOf(ItemTag("x", "h2", "drill", "t1", 1)))

        store.move(Selection(itemIds = listOf("drill")), Destination("h1", null))

        assertEquals(listOf("drill"), itemsIn(null))
        val custom = db.categories().custom("h1").single()
        assertEquals("Power tools", custom.name)
        assertEquals("tools", custom.parentId)
        assertEquals(listOf(custom.id, "tools.power"), db.categories().forItem("drill"))
        assertEquals(listOf("borrowed"), db.tags().forItem("drill").map { it.name })
        assertEquals("h1", db.tags().forItem("drill").single().houseId)
    }

    @Test
    fun existingCategoriesAndTagsInTheTargetAreReused() = runBlocking {
        db.categories().insertCustom(CustomCategory("pt2", "h2", "tools", "power tools", null, 1, 1))
        db.categories().insertCustom(CustomCategory("pt1", "h1", "tools", "Power Tools", null, 1, 1))
        db.items().insert(item("drill", houseId = "h2"))
        db.categories().insertAssignments(listOf(ItemCategory("a", "h2", "drill", "pt2", 0, 1)))

        store.move(Selection(itemIds = listOf("drill")), Destination("h1", null))

        assertEquals(listOf("pt1"), db.categories().forItem("drill"))
        assertEquals(1, db.categories().custom("h1").size)
    }

    @Test
    fun qrCodeClash() = runBlocking {
        db.items().insert(item("drill", houseId = "h2").copy(name = "Drill", qrCode = "Drill-01"))
        db.items().insert(item("other").copy(qrCode = "Drill-01"))

        val clashes = store.qrClashes(Selection(itemIds = listOf("drill")), Destination("h1", null))
        assertEquals(listOf(QrClash("drill", "Drill", "Drill-01")), clashes)
        try {
            store.move(Selection(itemIds = listOf("drill")), Destination("h1", null))
            error("must stop before moving")
        } catch (expected: IllegalStateException) {
            assertEquals("h2", db.items().get("drill")!!.houseId)
        }

        store.move(Selection(itemIds = listOf("drill")), Destination("h1", null), mapOf("drill" to "Drill-02"))
        assertEquals("Drill-02", db.items().get("drill")!!.qrCode)
    }

    @Test
    fun removingTheClashingCode() = runBlocking {
        db.items().insert(item("drill", houseId = "h2").copy(qrCode = "Drill-01"))
        db.items().insert(item("other").copy(qrCode = "Drill-01"))
        store.move(Selection(itemIds = listOf("drill")), Destination("h1", null), mapOf("drill" to null))
        assertNull(db.items().get("drill")!!.qrCode)
        assertEquals("h1", db.items().get("drill")!!.houseId)
    }

    @Test
    fun copyingALabelledBox() = runBlocking {
        (1..10).forEach { db.items().insert(item("i$it", containerId = "boxA").copy(qrCode = "I-$it")) }
        db.categories().insertAssignments(listOf(ItemCategory("a", "h1", "pi", "computers.sbcs", 0, 1)))

        val copied = store.copy(Selection(containerIds = listOf("boxA")), Destination("h1", "drawer")).single()

        val copyBox = db.containers().get(copied)!!
        assertEquals("Box A", copyBox.name)
        assertNull(copyBox.qrCode)
        assertEquals("BOX-A", db.containers().get("boxA")!!.qrCode)
        val copiedItems = db.items().observeIn("h1", copied).first()
        assertEquals(11, copiedItems.size)
        assertTrue(copiedItems.all { it.qrCode == null })
        val copiedCables = containersIn(copied).single()
        assertEquals(listOf("USB-C cable"), db.items().observeIn("h1", copiedCables).first().map { it.name })
        val piCopy = copiedItems.first { it.name == "Raspberry Pi 4" }
        assertEquals(listOf("computers.sbcs"), db.categories().forItem(piCopy.id))
        assertEquals(12, itemsIn("boxA").size - 1 + itemsIn("cables").size + 1)
    }

    @Test
    fun duplicatingAnItem() = runBlocking {
        db.items().insert(item("usb1", containerId = "cables").copy(name = "USB-C cable 1m", qrCode = "U1"))
        val id = store.duplicate("usb1", " (copy)")
        val copy = db.items().get(id)!!
        assertEquals("USB-C cable 1m (copy)", copy.name)
        assertEquals("cables", copy.containerId)
        assertNull(copy.qrCode)
    }

    @Test
    fun trashedThingsAreHiddenFromListsAndValues() = runBlocking {
        store.trash(Selection(itemIds = listOf("pi")))
        assertEquals(emptyList<String>(), itemsIn("boxA"))
        val totals = db.items().observeTotals("h1").first().associateBy { it.containerId }
        assertNull(totals["boxA"])
        assertEquals(ContainerTotal("cables", 1_500, 0, 1), totals["cables"])
    }

    @Test
    fun undoingADeleteRestoresExactly() = runBlocking {
        val entry = store.trash(Selection(containerIds = listOf("boxA"))).single()
        assertEquals(emptyList<String>(), containersIn(null).filter { it == "boxA" })

        assertEquals(RestoreResult(), store.restore(entry))

        assertEquals(listOf("boxA", "drawer"), containersIn(null))
        assertEquals(listOf("cables"), containersIn("boxA"))
        assertEquals(listOf("usb"), itemsIn("cables"))
        assertEquals(listOf("pi"), itemsIn("boxA"))
        assertEquals(emptyList<Any>(), db.organize().observeTrash("h1").first())
    }

    @Test
    fun normalRestoreKeepsSeparatelyTrashedThingsInTheTrash() = runBlocking {
        store.trash(Selection(itemIds = listOf("usb")))
        now += 1_000
        val bag = store.trash(Selection(containerIds = listOf("cables"))).single()

        store.restore(bag)

        assertEquals(listOf("cables"), containersIn("boxA"))
        assertEquals(emptyList<String>(), itemsIn("cables"))
        assertEquals(1, db.organize().observeTrash("h1").first().size)
    }

    @Test
    fun originalLocationGone() = runBlocking {
        val entry = store.trash(Selection(itemIds = listOf("usb"))).single()
        now += 1_000
        store.deletePermanently(store.trash(Selection(containerIds = listOf("cables"))).single())

        assertEquals(RestoreResult(needsDestination = true), store.restore(entry))
        assertEquals(RestoreResult(), store.restore(entry, Destination("h1", "drawer")))
        assertEquals(listOf("usb"), itemsIn("drawer"))
    }

    @Test
    fun restoredThingsLoseATakenQrCode() = runBlocking {
        db.items().insert(item("mouse").copy(name = "Mouse", qrCode = "M-1"))
        val entry = store.trash(Selection(itemIds = listOf("mouse"))).single()
        db.items().insert(item("newMouse").copy(qrCode = "M-1"))

        assertEquals(RestoreResult(qrRemoved = true), store.restore(entry))
        assertNull(db.items().get("mouse")!!.qrCode)
    }

    @Test
    fun automaticPurge() = runBlocking {
        store.trash(Selection(containerIds = listOf("boxA")))
        now += OrganizeStore.RETENTION_MS - 1
        assertEquals(0, store.purge())

        now += 2
        assertEquals(1, store.purge())

        assertNull(db.organize().containerAnyState("boxA"))
        assertNull(db.organize().containerAnyState("cables"))
        assertNull(db.organize().itemAnyState("usb"))
        assertEquals(emptyList<Any>(), db.organize().observeTrash("h1").first())
    }

    @Test
    fun deletingAHouseIsPermanent() = runBlocking {
        db.items().insert(item("x", houseId = "h2"))
        db.categories().insertCustom(CustomCategory("c", "h2", null, "Mine", null, 1, 1))
        store.deleteHouse("h2")
        assertNull(db.houses().get("h2"))
        assertNull(db.organize().itemAnyState("x"))
        assertEquals(emptyList<CustomCategory>(), db.categories().custom("h2"))
    }

    @Test(expected = IllegalStateException::class)
    fun theLastHouseCanNotBeDeleted() = runBlocking {
        store.deleteHouse("h2")
        store.deleteHouse("h1")
    }
}
