package app.trecos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * For benchmark builds only: stores one house with 20 containers and the
 * given number of items (1,000 by default) at the top level, for the
 * performance pass (task 14.8). Run with
 * `adb shell am broadcast -n app.trecos/.BenchmarkSeedReceiver --ei count 1000`.
 * In every other build it is disabled in the manifest and ignores any intent.
 */
class BenchmarkSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.BENCHMARK_SEED) return
        val count = intent.getIntExtra("count", 1000)
        val pending = goAsync()
        val app = (context.applicationContext as TrecosApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                if (db.houses().get(HOUSE) == null) {
                    db.houses().insert(House(HOUSE, "Benchmark house", icon = "house", createdAt = 1, updatedAt = 1))
                    repeat(20) { db.containers().insert(Container("box$it", HOUSE, null, "Box $it", icon = "box", createdAt = 1, updatedAt = 1)) }
                    repeat(count) {
                        db.items().insert(Item("item$it", HOUSE, null, "Item $it", quantity = 1 + it % 5, unitPrice = (it % 7) * 1_000L, createdAt = 1, updatedAt = 1))
                    }
                }
                app.preferences.setLastHouse(HOUSE)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val HOUSE = "benchmark-house"
    }
}
