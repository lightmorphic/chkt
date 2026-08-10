package org.chkt.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.chkt.app.data.Repository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Writes a dated JSON backup into the user's chosen folder once a day and
 * keeps the most recent 14, so a bad edit can be walked back two weeks.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val enabled = repo.settings.backupEnabled.first()
        val folder = repo.settings.backupFolder.first() ?: return Result.success()
        if (!enabled) return Result.success()

        return try {
            val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folder))
                ?: return Result.failure()
            val name = "chkt-backup-${LocalDate.now()}.json"
            // Replace today's backup if it already exists.
            tree.findFile(name)?.delete()
            val file = tree.createFile("application/json", name) ?: return Result.retry()
            val (lists, reminders) = ExportImport.snapshot(repo)
            applicationContext.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(ExportImport.exportJson(lists, reminders).toByteArray())
            }
            prune(tree)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun prune(tree: DocumentFile) {
        tree.listFiles()
            .filter { it.name?.startsWith("chkt-backup-") == true }
            .sortedByDescending { it.name }
            .drop(14)
            .forEach { it.delete() }
    }
}

object BackupScheduler {
    private const val WORK_NAME = "chkt-daily-backup"

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
