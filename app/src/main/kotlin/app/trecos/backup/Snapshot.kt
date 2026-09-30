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
import app.trecos.data.TrecosDatabase
import java.io.BufferedReader
import java.io.Writer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Every row of one house, trashed rows included (design D14, D15).
 *
 * @property house the house.
 * @property containers its containers.
 * @property items its items.
 * @property categories its custom categories.
 * @property itemCategories its category assignments.
 * @property tags its tags.
 * @property itemTags its tag assignments.
 * @property learned its learned category suggestions.
 * @property trash its trash entries.
 * @property photos its photo rows (the files travel separately, by SHA-256).
 * @property fieldDefs its custom field definitions.
 * @property fieldValues its custom field values.
 */
data class HouseSnapshot(
    val house: House,
    val containers: List<Container> = emptyList(),
    val items: List<Item> = emptyList(),
    val categories: List<CustomCategory> = emptyList(),
    val itemCategories: List<ItemCategory> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val itemTags: List<ItemTag> = emptyList(),
    val learned: List<TokenCategoryCount> = emptyList(),
    val trash: List<TrashEntry> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val fieldDefs: List<FieldDef> = emptyList(),
    val fieldValues: List<FieldValue> = emptyList(),
) {
    companion object {
        /**
         * Reads a house from the database.
         *
         * @param db the database.
         * @param house the house.
         * @return its snapshot.
         */
        suspend fun read(db: TrecosDatabase, house: House): HouseSnapshot = with(db.snapshots()) {
            HouseSnapshot(
                house, containers(house.id), items(house.id), categories(house.id), itemCategories(house.id), tags(house.id),
                itemTags(house.id), learned(house.id), trash(house.id), photos(house.id), fieldDefs(house.id), fieldValues(house.id),
            )
        }
    }
}

/** A snapshot file that can't be read. */
class SnapshotFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The snapshot format shared by backups and sync: JSON Lines, one row per
 * line as `{"t": table, "r": row}`, tables in a fixed order and rows sorted by
 * id, so the same data always gives the same bytes.
 */
object SnapshotFormat {
    /** The format version this app writes and the newest it reads. */
    const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    /**
     * One table: its name, serializer, how to sort its rows, and how to read and fill it.
     */
    private class Table<T>(
        val name: String,
        val serializer: KSerializer<T>,
        val key: (T) -> String,
        val rows: (HouseSnapshot) -> List<T>,
        val fill: (Builder, List<T>) -> Unit,
    )

    /** Collects rows while reading. */
    private class Builder {
        var house: House? = null
        val containers = mutableListOf<Container>()
        val items = mutableListOf<Item>()
        val categories = mutableListOf<CustomCategory>()
        val itemCategories = mutableListOf<ItemCategory>()
        val tags = mutableListOf<Tag>()
        val itemTags = mutableListOf<ItemTag>()
        val learned = mutableListOf<TokenCategoryCount>()
        val trash = mutableListOf<TrashEntry>()
        val photos = mutableListOf<Photo>()
        val fieldDefs = mutableListOf<FieldDef>()
        val fieldValues = mutableListOf<FieldValue>()
    }

    private val tables: List<Table<*>> = listOf(
        Table("house", House.serializer(), { it.id }, { listOf(it.house) }) { b, r -> b.house = r.single() },
        Table("container", Container.serializer(), { it.id }, { it.containers }) { b, r -> b.containers += r },
        Table("item", Item.serializer(), { it.id }, { it.items }) { b, r -> b.items += r },
        Table("category", CustomCategory.serializer(), { it.id }, { it.categories }) { b, r -> b.categories += r },
        Table("item_category", ItemCategory.serializer(), { it.id }, { it.itemCategories }) { b, r -> b.itemCategories += r },
        Table("tag", Tag.serializer(), { it.id }, { it.tags }) { b, r -> b.tags += r },
        Table("item_tag", ItemTag.serializer(), { it.id }, { it.itemTags }) { b, r -> b.itemTags += r },
        Table("token_category_count", TokenCategoryCount.serializer(), { "${it.token}\u0000${it.categoryId}" }, { it.learned }) { b, r -> b.learned += r },
        Table("trash_entry", TrashEntry.serializer(), { it.id }, { it.trash }) { b, r -> b.trash += r },
        Table("photo", Photo.serializer(), { it.id }, { it.photos }) { b, r -> b.photos += r },
        Table("field_def", FieldDef.serializer(), { it.id }, { it.fieldDefs }) { b, r -> b.fieldDefs += r },
        Table("field_value", FieldValue.serializer(), { it.id }, { it.fieldValues }) { b, r -> b.fieldValues += r },
    )
    private val byName = tables.associateBy { it.name }

    /**
     * The generic view the sync merge works on.
     *
     * @param snapshot a house.
     * @return every row as JSON, keyed by `table/key` (for example `item/<id>`).
     */
    fun toRows(snapshot: HouseSnapshot): Map<String, JsonObject> {
        val rows = LinkedHashMap<String, JsonObject>()
        tables.forEach { addRows(it, snapshot, rows) }
        return rows
    }

    /** Adds one table's rows to [rows]. */
    private fun <T> addRows(table: Table<T>, snapshot: HouseSnapshot, rows: MutableMap<String, JsonObject>) {
        table.rows(snapshot).sortedBy(table.key).forEach { row ->
            rows["${table.name}/${table.key(row)}"] = json.encodeToJsonElement(table.serializer, row).jsonObject
        }
    }

    /**
     * Builds a house back from rows made by [toRows] (and merged).
     *
     * @param rows the rows, keyed by `table/key`.
     * @return the house.
     * @throws SnapshotFormatException if a row can't be read or there isn't exactly one house.
     */
    fun fromRows(rows: Map<String, JsonObject>): HouseSnapshot {
        val grouped = rows.entries.groupBy({ it.key.substringBefore('/') }, { it.value })
        grouped.keys.firstOrNull { it !in byName }?.let { throw SnapshotFormatException("Unknown table \"$it\"") }
        if (grouped["house"]?.size != 1) throw SnapshotFormatException("A snapshot needs exactly one house")
        val builder = Builder()
        grouped.forEach { (name, list) -> fillTable(byName.getValue(name), list, builder) }
        return with(builder) {
            HouseSnapshot(house!!, containers, items, categories, itemCategories, tags, itemTags, learned, trash, photos, fieldDefs, fieldValues)
        }
    }

    /**
     * Writes a house.
     *
     * @param snapshot the house.
     * @param out where the lines go.
     */
    fun write(snapshot: HouseSnapshot, out: Writer) {
        tables.forEach { writeTable(it, snapshot, out) }
        out.flush()
    }

    /** Writes one table's rows, sorted. */
    private fun <T> writeTable(table: Table<T>, snapshot: HouseSnapshot, out: Writer) {
        table.rows(snapshot).sortedBy(table.key).forEach { row ->
            val line = buildJsonObject {
                put("t", table.name)
                put("r", json.encodeToJsonElement(table.serializer, row))
            }
            out.write(line.toString())
            out.write("\n")
        }
    }

    /**
     * Reads a house.
     *
     * @param reader the lines.
     * @return the house.
     * @throws SnapshotFormatException if a line isn't a known row, or there isn't exactly one house.
     */
    fun read(reader: BufferedReader): HouseSnapshot {
        val builder = Builder()
        val grouped = LinkedHashMap<String, MutableList<JsonObject>>()
        reader.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, line ->
            try {
                val obj = json.parseToJsonElement(line).jsonObject
                val table = obj.getValue("t").jsonPrimitive.content
                if (table !in byName) throw SnapshotFormatException("Unknown table \"$table\" on line ${index + 1}")
                grouped.getOrPut(table) { mutableListOf() } += obj.getValue("r").jsonObject
            } catch (e: SnapshotFormatException) {
                throw e
            } catch (e: Exception) {
                throw SnapshotFormatException("Line ${index + 1} can't be read", e)
            }
        }
        if (grouped["house"]?.size != 1) throw SnapshotFormatException("A snapshot needs exactly one house")
        grouped.forEach { (name, rows) -> fillTable(byName.getValue(name), rows, builder) }
        return with(builder) {
            HouseSnapshot(house!!, containers, items, categories, itemCategories, tags, itemTags, learned, trash, photos, fieldDefs, fieldValues)
        }
    }

    /** Decodes one table's rows into the builder. */
    private fun <T> fillTable(table: Table<T>, rows: List<JsonObject>, builder: Builder) {
        val decoded = try {
            rows.map { json.decodeFromJsonElement(table.serializer, it) }
        } catch (e: SerializationException) {
            throw SnapshotFormatException("A ${table.name} row can't be read", e)
        } catch (e: IllegalArgumentException) {
            throw SnapshotFormatException("A ${table.name} row can't be read", e)
        }
        table.fill(builder, decoded)
    }
}
