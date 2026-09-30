package app.trecos.data

import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps `search_index` in step with items and containers through SQLite
 * triggers, so every write path (forms, moves, trash, sync) updates search
 * without extra code. Trashed rows leave the index. An item's Text custom
 * field values are indexed as part of its description.
 */
object SearchIndex {

    private const val ITEM_NAME =
        "new.name || ' ' || COALESCE(new.brand, '') || ' ' || COALESCE(new.model, '') || ' ' || COALESCE(new.serial, '') || ' ' || COALESCE(new.qrCode, '')"
    private const val ITEM_DESCRIPTION =
        "COALESCE(new.description, '') || ' ' || COALESCE((SELECT group_concat(v.value, ' ') FROM field_value v " +
            "JOIN field_def d ON d.id = v.fieldId WHERE v.itemId = new.id AND d.type = 'Text'), '')"
    private const val CONTAINER_NAME = "new.name || ' ' || COALESCE(new.qrCode, '')"

    /** The trigger statements, safe to run more than once. */
    val triggers: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS search_item_insert AFTER INSERT ON item BEGIN " +
            "INSERT INTO search_index (kind, refId, houseId, name, description) " +
            "SELECT 'item', new.id, new.houseId, $ITEM_NAME, $ITEM_DESCRIPTION WHERE new.deletedAt IS NULL; END",
        "CREATE TRIGGER IF NOT EXISTS search_item_update AFTER UPDATE ON item BEGIN " +
            "DELETE FROM search_index WHERE kind = 'item' AND refId = old.id; " +
            "INSERT INTO search_index (kind, refId, houseId, name, description) " +
            "SELECT 'item', new.id, new.houseId, $ITEM_NAME, $ITEM_DESCRIPTION WHERE new.deletedAt IS NULL; END",
        "CREATE TRIGGER IF NOT EXISTS search_item_delete AFTER DELETE ON item BEGIN " +
            "DELETE FROM search_index WHERE kind = 'item' AND refId = old.id; END",
        "CREATE TRIGGER IF NOT EXISTS search_field_insert AFTER INSERT ON field_value BEGIN " +
            "UPDATE item SET id = id WHERE id = new.itemId; END",
        "CREATE TRIGGER IF NOT EXISTS search_field_update AFTER UPDATE ON field_value BEGIN " +
            "UPDATE item SET id = id WHERE id = new.itemId; END",
        "CREATE TRIGGER IF NOT EXISTS search_field_delete AFTER DELETE ON field_value BEGIN " +
            "UPDATE item SET id = id WHERE id = old.itemId; END",
        "CREATE TRIGGER IF NOT EXISTS search_container_insert AFTER INSERT ON container BEGIN " +
            "INSERT INTO search_index (kind, refId, houseId, name, description) " +
            "SELECT 'container', new.id, new.houseId, $CONTAINER_NAME, COALESCE(new.description, '') WHERE new.deletedAt IS NULL; END",
        "CREATE TRIGGER IF NOT EXISTS search_container_update AFTER UPDATE ON container BEGIN " +
            "DELETE FROM search_index WHERE kind = 'container' AND refId = old.id; " +
            "INSERT INTO search_index (kind, refId, houseId, name, description) " +
            "SELECT 'container', new.id, new.houseId, $CONTAINER_NAME, COALESCE(new.description, '') WHERE new.deletedAt IS NULL; END",
        "CREATE TRIGGER IF NOT EXISTS search_container_delete AFTER DELETE ON container BEGIN " +
            "DELETE FROM search_index WHERE kind = 'container' AND refId = old.id; END",
    )

    /** Fills the index from existing rows. */
    val backfill: List<String> = listOf(
        "DELETE FROM search_index",
        "INSERT INTO search_index (kind, refId, houseId, name, description) SELECT 'item', id, houseId, " +
            "name || ' ' || COALESCE(brand, '') || ' ' || COALESCE(model, '') || ' ' || COALESCE(serial, '') || ' ' || COALESCE(qrCode, ''), " +
            "COALESCE(description, '') || ' ' || COALESCE((SELECT group_concat(v.value, ' ') FROM field_value v " +
            "JOIN field_def d ON d.id = v.fieldId WHERE v.itemId = item.id AND d.type = 'Text'), '') FROM item WHERE deletedAt IS NULL",
        "INSERT INTO search_index (kind, refId, houseId, name, description) SELECT 'container', id, houseId, " +
            "name || ' ' || COALESCE(qrCode, ''), COALESCE(description, '') FROM container WHERE deletedAt IS NULL",
    )

    /**
     * Creates the triggers and fills the index.
     *
     * @param db the open database.
     */
    fun install(db: SupportSQLiteDatabase) {
        triggers.forEach(db::execSQL)
        backfill.forEach(db::execSQL)
    }

    /** Installs the triggers on a newly created database. */
    val onCreate = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = install(db)
    }

    /**
     * The 4 → 5 migration's extra step: triggers and backfill after Room creates
     * the table, frozen as they were in schema 5 (custom fields don't exist yet;
     * the 5 → 6 step replaces the item triggers).
     */
    class Migration4To5 : AutoMigrationSpec {
        /**
         * @param db the database being migrated.
         */
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val name = "new.name || ' ' || COALESCE(new.brand, '') || ' ' || COALESCE(new.model, '') || ' ' || COALESCE(new.serial, '') || ' ' || COALESCE(new.qrCode, '')"
            val insert = "INSERT INTO search_index (kind, refId, houseId, name, description) " +
                "SELECT 'item', new.id, new.houseId, $name, COALESCE(new.description, '') WHERE new.deletedAt IS NULL; END"
            db.execSQL("CREATE TRIGGER IF NOT EXISTS search_item_insert AFTER INSERT ON item BEGIN $insert")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS search_item_update AFTER UPDATE ON item BEGIN DELETE FROM search_index WHERE kind = 'item' AND refId = old.id; $insert")
            triggers.filter { "ON container" in it || "search_item_delete" in it }.forEach(db::execSQL)
            db.execSQL("DELETE FROM search_index")
            db.execSQL(
                "INSERT INTO search_index (kind, refId, houseId, name, description) SELECT 'item', id, houseId, " +
                    "name || ' ' || COALESCE(brand, '') || ' ' || COALESCE(model, '') || ' ' || COALESCE(serial, '') || ' ' || COALESCE(qrCode, ''), " +
                    "COALESCE(description, '') FROM item WHERE deletedAt IS NULL",
            )
            db.execSQL(backfill.last())
        }
    }

    /** The 5 → 6 migration's extra step: item triggers that also index custom field values. */
    class Migration5To6 : AutoMigrationSpec {
        /**
         * @param db the database being migrated.
         */
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            listOf("search_item_insert", "search_item_update").forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
            install(db)
        }
    }
}
