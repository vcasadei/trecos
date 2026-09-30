package app.trecos.places

import app.trecos.data.ContainerTotal
import app.trecos.data.Fixtures.container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scenarios "Override counts upward" and "Unpriced items", plus paths and
 * colour inheritance.
 */
class PlaceTreeTest {

    private val office = container("office")
    private val boxA = container("boxA", parentId = "office")
    private val boxB = container("boxB", parentId = "office").copy(valueOverride = 50_000)

    @Test
    fun overrideCountsUpward() {
        val tree = PlaceTree(
            containers = listOf(office, boxA, boxB),
            totals = listOf(
                ContainerTotal("office", total = 350_000, unpriced = 0, items = 1),
                ContainerTotal("boxA", total = 22_000, unpriced = 0, items = 2),
                ContainerTotal("boxB", total = 0, unpriced = 4, items = 4),
            ),
        )
        assertEquals(PlaceValue(422_000, manual = false, unpriced = 4, items = 7), tree.value("office"))
        assertEquals(PlaceValue(50_000, manual = true, unpriced = 4, items = 4), tree.value("boxB"))
        assertEquals(422_000L, tree.houseValue.value)
    }

    @Test
    fun unpricedItems() {
        val tree = PlaceTree(listOf(office), listOf(ContainerTotal("office", total = 1_000, unpriced = 3)))
        assertEquals(3, tree.value("office")?.unpriced)
    }

    @Test
    fun houseValueIncludesTopLevelItems() {
        val tree = PlaceTree(listOf(office), listOf(ContainerTotal(null, 500, 1), ContainerTotal("office", 1_000, 0)))
        assertEquals(PlaceValue(1_500, manual = false, unpriced = 1), tree.houseValue)
    }

    @Test
    fun pathRunsFromTheTopLevelDown() {
        val cables = container("cables", parentId = "boxA")
        val tree = PlaceTree(listOf(office, boxA, cables), emptyList())
        assertEquals(listOf("office", "boxA", "cables"), tree.path("cables").map { it.id })
        assertEquals(emptyList<String>(), tree.path(null).map { it.id })
        assertNull(tree.value("missing"))
        assertEquals(2, tree.childCount(null) + tree.childCount("office"))
    }

    @Test
    fun inheritedColourAndOwnColourWins() {
        val blueOffice = office.copy(colorKey = "blue")
        val orangeBoxB = boxB.copy(colorKey = "coral")
        val tree = PlaceTree(listOf(blueOffice, boxA, orangeBoxB), emptyList())
        assertEquals("blue", tree.colorKey("boxA"))
        assertEquals("coral", tree.colorKey("boxB"))
        assertNull(PlaceTree(listOf(office), emptyList()).colorKey("office"))
    }

    @Test
    fun aCycleInCorruptDataDoesNotHang() {
        val a = container("a", parentId = "b")
        val b = container("b", parentId = "a")
        val tree = PlaceTree(listOf(a, b), emptyList())
        assertEquals(0L, tree.value("a")?.value)
        assertEquals(2, tree.path("a").size)
    }
}
