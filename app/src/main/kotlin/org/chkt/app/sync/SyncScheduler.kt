package org.chkt.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val message = SyncClient(applicationContext).syncNow()
        return if (message.startsWith("Sync failed")) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private const val WORK_NAME = "chkt-sync"

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // UPDATE, not KEEP: installs from when this ran hourly keep the
            // old interval forever under KEEP — the policy change would
            // never reach them.
            ExistingPeriodicWorkPolicy.UPDATE,
            // 15 minutes is Android's floor for periodic work. A reminder
            // created on the calendar or web can't sit unseen for an hour
            // any more; opening the app narrows the gap to seconds.
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
