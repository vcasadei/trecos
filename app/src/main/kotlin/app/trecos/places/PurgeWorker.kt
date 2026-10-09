package app.trecos.places

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.trecos.TrecosApplication
import java.util.concurrent.TimeUnit

/**
 * The daily maintenance job: permanently removes whatever has been in any
 * house's trash for more than 30 days.
 *
 * @param context the application context.
 * @param params the worker parameters.
 */
class PurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * Purges old trash.
     *
     * @return success; a failure is retried the next day.
     */
    override suspend fun doWork(): Result {
        (applicationContext as TrecosApplication).container.organize.purge()
        return Result.success()
    }

    companion object {
        private const val NAME = "purge-trash"

        /**
         * Schedules the daily purge once; later calls keep the existing schedule.
         *
         * @param context any context of the app.
         */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
