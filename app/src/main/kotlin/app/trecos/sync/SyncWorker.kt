package app.trecos.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.trecos.TrecosApplication
import app.trecos.data.SyncFrequency
import java.util.concurrent.TimeUnit

/**
 * The background sync (spec "Schedule"): runs at the chosen frequency, only
 * with a network, and is retried with backoff when Drive can't be reached.
 *
 * @param context the application context.
 * @param params the worker parameters.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * Syncs every house.
     *
     * @return success, or retry when it may work later.
     */
    override suspend fun doWork(): Result {
        val sync = (applicationContext as TrecosApplication).container.sync
        if (!sync.store.load().connected) return Result.success()
        if (sync.syncNow()) return Result.success()
        val state = sync.store.load()
        // Errors a retry can't fix wait for the user; the others are retried.
        val waitsForUser = state.driveMissing || state.lastError == applicationContext.getString(app.trecos.R.string.sync_error_auth) ||
            state.lastError == applicationContext.getString(app.trecos.R.string.sync_error_newer)
        return if (waitsForUser) Result.success() else Result.retry()
    }

    companion object {
        /** The unique name of the periodic sync. */
        const val NAME = "drive-sync"

        /**
         * Schedules the periodic sync, replacing any earlier schedule, or cancels it for [SyncFrequency.Never].
         *
         * @param context any context of the app.
         * @param frequency how often to sync.
         */
        fun schedule(context: Context, frequency: SyncFrequency) {
            val work = WorkManager.getInstance(context)
            val days = frequency.days
            if (days == null) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(days.toLong(), TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Stops background syncs.
         *
         * @param context any context of the app.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
