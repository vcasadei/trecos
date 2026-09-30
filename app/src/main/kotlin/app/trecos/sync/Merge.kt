package app.trecos.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Rows of one house, keyed by `table/key` (see `SnapshotFormat.toRows`). */
typealias Rows = Map<String, JsonObject>

/** What kind of disagreement a conflict is. */
@Serializable
enum class ConflictKind {
    /** The same field changed to different values on both sides. */
    SameField,

    /** One side deleted or trashed the row while the other edited it. */
    EditVersusDelete,
}

/**
 * A disagreement the user resolves on the conflict screen (spec "Merging and
 * conflicts"). Until then the local value stays in effect.
 *
 * @property key the row, `table/key`.
 * @property kind what kind of disagreement.
 * @property field the field, for [ConflictKind.SameField].
 * @property mine the local value (field value, or the local row; `null` when deleted here).
 * @property theirs the other device's value (field value, or its row; `null` when deleted there).
 * @property deletedHere for [ConflictKind.EditVersusDelete], whether this device is the one that deleted or trashed it.
 */
@Serializable
data class Conflict(
    val key: String,
    val kind: ConflictKind,
    val field: String? = null,
    val mine: JsonElement? = null,
    val theirs: JsonElement? = null,
    val deletedHere: Boolean = false,
) {
    /** The table, such as `item`. */
    val table: String get() = key.substringBefore('/')

    /** The row's key within its table. */
    val rowKey: String get() = key.substringAfter('/')
}

/**
 * The result of a merge.
 *
 * @property rows the merged rows, with local values kept wherever there is a conflict.
 * @property conflicts the disagreements for the user.
 * @property changedHere how many rows the merge changed compared with the local rows.
 */
data class MergeResult(val rows: Rows, val conflicts: List<Conflict>, val changedHere: Int)

/**
 * The three-way merge of design D14, per field: a field changed on only one
 * side takes that side, the same field changed on both sides becomes a
 * conflict, and an edit on one side against a delete (or a move to the trash)
 * on the other is a conflict too. Plain Kotlin, no Android.
 */
object Merge {
    private const val DELETED_AT = "deletedAt"

    /**
     * Merges [theirs] into [mine], with [base] their last common state.
     *
     * @param base the last state both sides agreed on; empty when there is none.
     * @param mine this device's rows.
     * @param theirs the other device's rows.
     * @return the merged rows and the conflicts.
     */
    fun merge(base: Rows, mine: Rows, theirs: Rows): MergeResult {
        val merged = LinkedHashMap<String, JsonObject>()
        val conflicts = ArrayList<Conflict>()
        for (key in (mine.keys + theirs.keys + base.keys).toSortedSet()) {
            val b = base[key]
            val m = mine[key]
            val t = theirs[key]
            val result: JsonObject? = when {
                m == t -> m
                b == null && m == null -> t
                b == null && t == null -> m
                m == null -> if (t == b) null else conflict(conflicts, Conflict(key, ConflictKind.EditVersusDelete, mine = null, theirs = t, deletedHere = true), keep = null)
                t == null -> if (m == b) null else conflict(conflicts, Conflict(key, ConflictKind.EditVersusDelete, mine = m, theirs = null, deletedHere = false), keep = m)
                else -> mergeRow(key, b ?: JsonObject(emptyMap()), m, t, conflicts)
            }
            if (result != null) merged[key] = result
        }
        val changed = merged.count { (k, v) -> mine[k] != v } + mine.keys.count { it !in merged }
        return MergeResult(merged, conflicts, changed)
    }

    /**
     * Folds several devices' rows into this device's, one at a time.
     *
     * @param mine this device's rows.
     * @param others each other device's rows with the base this device shares with it.
     * @return the merged rows and every conflict.
     */
    fun fold(mine: Rows, others: List<Pair<Rows, Rows>>): MergeResult {
        var current = mine
        val conflicts = ArrayList<Conflict>()
        for ((base, theirs) in others) {
            val step = merge(base, current, theirs)
            current = step.rows
            conflicts += step.conflicts
        }
        val changed = current.count { (k, v) -> mine[k] != v } + mine.keys.count { it !in current }
        return MergeResult(current, conflicts.distinctBy { it.key to it.field }, changed)
    }

    /** Records a conflict and returns the value to keep meanwhile. */
    private fun conflict(conflicts: MutableList<Conflict>, conflict: Conflict, keep: JsonObject?): JsonObject? {
        conflicts += conflict
        return keep
    }

    /** Merges one row present on both sides, field by field. */
    private fun mergeRow(key: String, b: JsonObject, m: JsonObject, t: JsonObject, conflicts: MutableList<Conflict>): JsonObject {
        val trashedHere = isTrashed(m) && !isTrashed(b)
        val trashedThere = isTrashed(t) && !isTrashed(b)
        val editedHere = changedFields(b, m).any { it != DELETED_AT }
        val editedThere = changedFields(b, t).any { it != DELETED_AT }
        if (trashedHere && !trashedThere && editedThere) {
            conflicts += Conflict(key, ConflictKind.EditVersusDelete, mine = m, theirs = t, deletedHere = true)
            return m
        }
        if (trashedThere && !trashedHere && editedHere) {
            conflicts += Conflict(key, ConflictKind.EditVersusDelete, mine = m, theirs = t, deletedHere = false)
            return m
        }
        val fields = LinkedHashMap<String, JsonElement>()
        for (field in (m.keys + t.keys + b.keys).toSortedSet()) {
            val bv = b[field] ?: JsonNull
            val mv = m[field] ?: JsonNull
            val tv = t[field] ?: JsonNull
            fields[field] = when {
                mv == tv -> mv
                mv == bv -> tv
                tv == bv -> mv
                else -> {
                    conflicts += Conflict(key, ConflictKind.SameField, field = field, mine = mv, theirs = tv)
                    mv
                }
            }
        }
        return JsonObject(fields)
    }

    /** @return the fields whose values differ between two versions of a row. */
    private fun changedFields(a: JsonObject, b: JsonObject): Set<String> =
        (a.keys + b.keys).filterTo(HashSet()) { (a[it] ?: JsonNull) != (b[it] ?: JsonNull) }

    /** @return whether a row has a `deletedAt` time (it is in the trash). */
    private fun isTrashed(row: JsonObject): Boolean {
        val value = row[DELETED_AT] ?: return false
        return value is JsonPrimitive && value !is JsonNull && value.jsonPrimitive.contentOrNull != null
    }
}
