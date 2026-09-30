package app.trecos.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustive tests of the three-way merge (task 12.5, spec "Merging and conflicts"). */
class MergeTest {

    /** @return a row with the fields, numbers and strings as given, nulls as JSON null. */
    private fun row(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(
        fields.associate { (k, v) ->
            k to when (v) {
                null -> JsonNull
                is Number -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            } as JsonElement
        },
    )

    private val pi = row("id" to "pi", "name" to "Pi", "quantity" to 1, "description" to null, "deletedAt" to null)
    private val key = "item/pi"

    /** @return a copy of a row with some fields replaced. */
    private fun JsonObject.with(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(this + row(*fields))

    @Test
    fun noConflict() {
        val mine = pi.with("quantity" to 2)
        val theirs = pi.with("description" to "4 GB")
        val result = Merge.merge(mapOf(key to pi), mapOf(key to mine), mapOf(key to theirs))

        assertEquals(pi.with("quantity" to 2, "description" to "4 GB"), result.rows[key])
        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, result.changedHere)
    }

    @Test
    fun sameField() {
        val result = Merge.merge(mapOf(key to pi), mapOf(key to pi.with("quantity" to 2)), mapOf(key to pi.with("quantity" to 5)))

        val conflict = result.conflicts.single()
        assertEquals(ConflictKind.SameField, conflict.kind)
        assertEquals("quantity", conflict.field)
        assertEquals(JsonPrimitive(2), conflict.mine)
        assertEquals(JsonPrimitive(5), conflict.theirs)
        assertEquals("the local value stays until resolved", pi.with("quantity" to 2), result.rows[key])
        assertEquals(0, result.changedHere)
    }

    @Test
    fun sameFieldSameValueIsNoConflict() {
        val both = pi.with("quantity" to 3)
        val result = Merge.merge(mapOf(key to pi), mapOf(key to both), mapOf(key to both))
        assertTrue(result.conflicts.isEmpty())
        assertEquals(both, result.rows[key])
    }

    @Test
    fun editVersusDeleteWithTheTrash() {
        val trashedHere = pi.with("deletedAt" to 99)
        val editedThere = pi.with("name" to "Raspberry Pi")
        val here = Merge.merge(mapOf(key to pi), mapOf(key to trashedHere), mapOf(key to editedThere))
        assertEquals(ConflictKind.EditVersusDelete, here.conflicts.single().kind)
        assertTrue(here.conflicts.single().deletedHere)
        assertEquals(trashedHere, here.rows[key])

        val there = Merge.merge(mapOf(key to pi), mapOf(key to editedThere), mapOf(key to trashedHere))
        assertEquals(ConflictKind.EditVersusDelete, there.conflicts.single().kind)
        assertFalse(there.conflicts.single().deletedHere)
        assertEquals(editedThere, there.rows[key])
    }

    @Test
    fun trashingWithoutEditsElsewhereMergesLikeAField() {
        val trashed = pi.with("deletedAt" to 99)
        val result = Merge.merge(mapOf(key to pi), mapOf(key to pi), mapOf(key to trashed))
        assertEquals(trashed, result.rows[key])
        assertTrue(result.conflicts.isEmpty())

        val restoredThere = Merge.merge(mapOf(key to trashed), mapOf(key to trashed), mapOf(key to pi))
        assertEquals(pi, restoredThere.rows[key])
    }

    @Test
    fun editVersusPermanentDelete() {
        val edited = pi.with("quantity" to 4)
        val deletedHere = Merge.merge(mapOf(key to pi), emptyMap(), mapOf(key to edited))
        assertNull(deletedHere.rows[key])
        assertEquals(Conflict(key, ConflictKind.EditVersusDelete, mine = null, theirs = edited, deletedHere = true), deletedHere.conflicts.single())

        val deletedThere = Merge.merge(mapOf(key to pi), mapOf(key to edited), emptyMap())
        assertEquals(edited, deletedThere.rows[key])
        assertFalse(deletedThere.conflicts.single().deletedHere)
    }

    @Test
    fun deletesOfUnchangedRowsWin() {
        assertNull(Merge.merge(mapOf(key to pi), emptyMap(), mapOf(key to pi)).rows[key])
        assertNull(Merge.merge(mapOf(key to pi), mapOf(key to pi), emptyMap()).rows[key])
        assertNull(Merge.merge(mapOf(key to pi), emptyMap(), emptyMap()).rows[key])
    }

    @Test
    fun additionsOnEitherSideAreKept() {
        val mouse = row("id" to "mouse", "name" to "Mouse")
        val kb = row("id" to "kb", "name" to "Keyboard")
        val result = Merge.merge(emptyMap(), mapOf("item/mouse" to mouse), mapOf("item/kb" to kb))
        assertEquals(setOf("item/mouse", "item/kb"), result.rows.keys)
        assertEquals(1, result.changedHere)
    }

    @Test
    fun theSameRowAddedTwiceWithoutABase() {
        val a = row("id" to "x", "name" to "Box", "icon" to "box")
        val b = row("id" to "x", "name" to "Crate", "icon" to "box")
        val result = Merge.merge(emptyMap(), mapOf("container/x" to a), mapOf("container/x" to b))
        assertEquals("name", result.conflicts.single().field)
        assertEquals(a, result.rows["container/x"])
    }

    @Test
    fun newFieldsFromANewerRowAreMerged() {
        val withUnit = pi.with("unit" to "GB")
        val result = Merge.merge(mapOf(key to pi), mapOf(key to pi), mapOf(key to withUnit))
        assertEquals(withUnit, result.rows[key])
    }

    @Test
    fun foldingSeveralDevices() {
        val base = mapOf(key to pi)
        val tablet = mapOf(key to pi.with("description" to "4 GB"))
        val laptop = mapOf(key to pi.with("name" to "Raspberry Pi 4"))
        val result = Merge.fold(mapOf(key to pi.with("quantity" to 3)), listOf(base to tablet, base to laptop))

        assertEquals(pi.with("quantity" to 3, "description" to "4 GB", "name" to "Raspberry Pi 4"), result.rows[key])
        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, result.changedHere)
    }

    @Test
    fun foldingReportsEachConflictOnce() {
        val base = mapOf(key to pi)
        val two = mapOf(key to pi.with("quantity" to 2))
        val result = Merge.fold(mapOf(key to pi.with("quantity" to 9)), listOf(base to two, base to two))
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun conflictKnowsItsTableAndRow() {
        val conflict = Conflict("field_value/v1", ConflictKind.SameField, "value")
        assertEquals("field_value", conflict.table)
        assertEquals("v1", conflict.rowKey)
    }
}
