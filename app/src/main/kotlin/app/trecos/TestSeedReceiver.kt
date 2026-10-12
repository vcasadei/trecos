package app.trecos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.Photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * For test builds only (debug and benchmarkRelease): stores a named set of
 * fixture data so device flows and the benchmark start from a known state.
 * Run with
 * `adb shell am broadcast -n app.trecos/.TestSeedReceiver --es fixture two-houses`.
 * The fixtures are [FIXTURES]; `1000-items` is the performance pass's data
 * (task 14.8), and an `--ei count` extra changes its item count. In release
 * builds the receiver is disabled in the manifest and ignores any intent.
 */
class TestSeedReceiver : BroadcastReceiver() {

    /**
     * Stores the fixture named by the `fixture` extra, then makes its first house the current one.
     *
     * @param context the receiving context.
     * @param intent the broadcast; its `fixture` extra defaults to `1000-items`.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (!enabled) return
        val fixture = intent.getStringExtra("fixture") ?: THOUSAND_ITEMS
        if (fixture !in FIXTURES) return
        val count = intent.getIntExtra("count", 1000)
        val pending = goAsync()
        val app = (context.applicationContext as TrecosApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seed(context, app, fixture, count)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Writes one fixture, skipping it when its first house already exists.
     *
     * @param context reads the cache directory for generated photos.
     * @param app the app's container.
     * @param fixture one of [FIXTURES].
     * @param count the item count for `1000-items`.
     */
    private suspend fun seed(context: Context, app: AppContainer, fixture: String, count: Int) {
        val db = app.database
        val first = if (fixture == THOUSAND_ITEMS) BENCHMARK_HOUSE else APARTMENT
        if (db.houses().get(first) != null) return
        when (fixture) {
            THOUSAND_ITEMS -> {
                db.houses().insert(House(BENCHMARK_HOUSE, "Benchmark house", icon = "house", createdAt = 1, updatedAt = 1))
                repeat(20) { db.containers().insert(Container("box$it", BENCHMARK_HOUSE, null, "Box $it", icon = "box", createdAt = 1, updatedAt = 1)) }
                repeat(count) {
                    db.items().insert(Item("item$it", BENCHMARK_HOUSE, null, "Item $it", quantity = 1 + it % 5, unitPrice = (it % 7) * 1_000L, createdAt = 1, updatedAt = 1))
                }
            }
            else -> {
                apartment().forEach { insert(app, it) }
                if (fixture == TWO_HOUSES) parents().forEach { insert(app, it) }
                if (fixture == PHOTOS) addPhotos(context, app)
            }
        }
        app.preferences.setLastHouse(first)
    }

    private suspend fun insert(app: AppContainer, record: Any) {
        val db = app.database
        when (record) {
            is House -> db.houses().insert(record)
            is Container -> db.containers().insert(record)
            is Item -> db.items().insert(record)
        }
    }

    /** One house with an office, two boxes inside it and four items, one without a price. */
    private fun apartment(): List<Any> = listOf(
        House(APARTMENT, "Apartment", icon = "apartment", colorKey = "sky", createdAt = 1, updatedAt = 1),
        Container("office", APARTMENT, null, "Office", icon = "box", colorKey = "sky", createdAt = 1, updatedAt = 1),
        Container("box-a", APARTMENT, "office", "Box A", icon = "box", createdAt = 1, updatedAt = 1),
        Container("box-b", APARTMENT, "office", "Box B", icon = "drawer", colorKey = "coral", createdAt = 1, updatedAt = 1),
        Item("laptop", APARTMENT, "office", "Laptop", unitPrice = 350_000, brand = "Lenovo", model = "T14", serial = "PF-12345", createdAt = 1, updatedAt = 1),
        Item("usb-cable", APARTMENT, "box-a", "USB-C cable 1m", quantity = 3, unitPrice = 2_990, createdAt = 1, updatedAt = 1),
        Item("usb-hub", APARTMENT, "office", "USB hub 7 ports", unitPrice = 15_900, createdAt = 1, updatedAt = 1),
        Item("mystery", APARTMENT, "box-b", "Unlabelled cable", createdAt = 1, updatedAt = 1),
    )

    /** A second house with one box and two items, for the house list and moves between houses. */
    private fun parents(): List<Any> = listOf(
        House(PARENTS, "Parents' house", icon = "house", colorKey = "coral", createdAt = 2, updatedAt = 2),
        Container("attic", PARENTS, null, "Attic", icon = "box", createdAt = 2, updatedAt = 2),
        Item("lamp", PARENTS, "attic", "Desk lamp", unitPrice = 12_000, createdAt = 2, updatedAt = 2),
        Item("usb-cable-2", PARENTS, "attic", "USB-C cable 2m", createdAt = 2, updatedAt = 2),
    )

    /** Gives the laptop three generated photos, stored through the real photo pipeline. */
    private suspend fun addPhotos(context: Context, app: AppContainer) {
        listOf(Color.RED, Color.GREEN, Color.BLUE).forEachIndexed { index, color ->
            val file = File(context.cacheDir, "fixture-$index.png")
            val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val sha = app.photoStore.import(Uri.fromFile(file)) ?: return@forEachIndexed
            file.delete()
            app.database.photos().insert(listOf(Photo("photo-$index", APARTMENT, Photo.OWNER_ITEM, "laptop", sha, index, createdAt = 1)))
        }
    }

    companion object {
        /** Whether seeding works: true only in test builds; tests turn it off to check a release build. */
        @androidx.annotation.VisibleForTesting
        var enabled = BuildConfig.TEST_SEED

        /** The fixture sets, by the name the `fixture` extra takes. */
        val FIXTURES = setOf("one-house", "two-houses", "photos", "1000-items")

        private const val THOUSAND_ITEMS = "1000-items"
        private const val TWO_HOUSES = "two-houses"
        private const val PHOTOS = "photos"
        private const val BENCHMARK_HOUSE = "benchmark-house"
        private const val APARTMENT = "apartment"
        private const val PARENTS = "parents"
    }
}
