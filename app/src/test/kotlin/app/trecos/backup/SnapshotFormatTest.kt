package app.trecos.backup

import app.trecos.data.Container
import app.trecos.data.CustomCategory
import app.trecos.data.FieldDef
import app.trecos.data.FieldValue
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.ItemCategory
import app.trecos.data.ItemTag
import app.trecos.data.Photo
import app.trecos.data.Tag
import app.trecos.data.TokenCategoryCount
import app.trecos.data.TrashEntry
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round trips of the snapshot format for every entity type (task 11.1). */
class SnapshotFormatTest {

    private val house = House("h1", "Apartment", address = "Rua A, 10", description = "Main", icon = "house", colorKey = "mint", createdAt = 1, updatedAt = 2)

    /** A house with at least one row of every table, including nulls, trash and non-ASCII text. */
    private val full = HouseSnapshot(
        house = house,
        containers = listOf(
            Container("c2", "h1", "c1", "Caixa \"B\"", description = null, qrCode = "BOX-B", icon = "box", colorKey = null, valueOverride = 50_000, createdAt = 3, updatedAt = 4, deletedAt = 9),
            Container("c1", "h1", null, "Escritório", "Home office\nsecond line", null, "desk", "blue", null, 1, 1),
        ),
        items = listOf(Item("i1", "h1", "c1", "Cabeça de impressão", 2, 3_500, "Ender", "3 V2", "SN4478X21", "Q-1", "Spare 🙂", 5, 6)),
        categories = listOf(CustomCategory("cat1", "h1", "cables", "USB-C curto", "cable", 1, 1)),
        itemCategories = listOf(ItemCategory("ic1", "h1", "i1", "cat1", 0, 1)),
        tags = listOf(Tag("t1", "h1", "Emprestado", "emprestado", 1, 1)),
        itemTags = listOf(ItemTag("it1", "h1", "i1", "t1", 1)),
        learned = listOf(TokenCategoryCount("h1", "cabeca", "cat1", 3), TokenCategoryCount("h1", "cabeca", "3d", 1)),
        trash = listOf(TrashEntry("tr1", "h1", TrashEntry.KIND_CONTAINER, "c2", "Caixa \"B\"", "c1", 9)),
        photos = listOf(Photo("p1", "h1", Photo.OWNER_ITEM, "i1", "ab".repeat(32), 0, 1)),
        fieldDefs = listOf(FieldDef("f1", "h1", null, "RAM", "Number", "GB", 0, 1, 1), FieldDef("f2", "h1", "i1", "Firmware", "Text", null, 0, 1, 1)),
        fieldValues = listOf(FieldValue("v1", "h1", "i1", "f1", "4", 1, 1), FieldValue("v2", "h1", "i1", "f2", "Klipper", 1, 1)),
    )

    /** @return the snapshot as text. */
    private fun HouseSnapshot.text() = StringWriter().also { SnapshotFormat.write(this, it) }.toString()

    /** @return the snapshot read back from text. */
    private fun String.snapshot() = SnapshotFormat.read(reader().buffered())

    /** Compares two snapshots ignoring row order. */
    private fun assertSame(expected: HouseSnapshot, actual: HouseSnapshot) {
        assertEquals(expected.house, actual.house)
        listOf(
            expected.containers to actual.containers, expected.items to actual.items, expected.categories to actual.categories,
            expected.itemCategories to actual.itemCategories, expected.tags to actual.tags, expected.itemTags to actual.itemTags,
            expected.learned to actual.learned, expected.trash to actual.trash, expected.photos to actual.photos,
            expected.fieldDefs to actual.fieldDefs, expected.fieldValues to actual.fieldValues,
        ).forEach { (e, a) -> assertEquals(e.toSet(), a.toSet()) }
    }

    @Test
    fun everyEntityRoundTrips() {
        assertSame(full, full.text().snapshot())
    }

    @Test
    fun outputIsSortedAndStable() {
        val text = full.text()
        val shuffled = full.copy(containers = full.containers.reversed(), learned = full.learned.reversed())
        assertEquals(text, shuffled.text())
        val lines = text.lines().filter(String::isNotEmpty)
        assertTrue(lines.first().startsWith("{\"t\":\"house\""))
        assertEquals(1 + 2 + 1 + 1 + 1 + 1 + 1 + 2 + 1 + 1 + 2 + 2, lines.size)
        val containerLines = lines.filter { it.startsWith("{\"t\":\"container\"") }
        assertTrue(containerLines[0].contains("\"id\":\"c1\""))
    }

    @Test
    fun emptyHouseRoundTrips() {
        val empty = HouseSnapshot(house)
        assertSame(empty, empty.text().snapshot())
    }

    @Test
    fun damagedInputIsRejected() {
        assertThrows(SnapshotFormatException::class.java) { "not json".snapshot() }
        assertThrows(SnapshotFormatException::class.java) { "{\"t\":\"spaceship\",\"r\":{}}".snapshot() }
        assertThrows(SnapshotFormatException::class.java) { "".snapshot() }
        assertThrows(SnapshotFormatException::class.java) { full.text().replace("\"name\":\"Apartment\",", "").snapshot() }
        val twoHouses = full.text() + full.text().lines().first() + "\n"
        assertThrows(SnapshotFormatException::class.java) { twoHouses.snapshot() }
    }
}
