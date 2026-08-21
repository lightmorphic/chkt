package org.chkt.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chkt.app.BuildConfig
import org.chkt.app.R
import org.chkt.app.alarm.Notifications
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Update checks against the project's GitHub releases, nothing else. Checking
 * is manual (a button in Settings) or an opt-in daily check, off by default,
 * so the app never phones anywhere without being asked. Updating is one tap:
 * the release APK downloads and Android's own installer takes over.
 */
object Updater {
    // The canonical repo is lightmorphic/chkt. Never rely on a redirect here:
    // GitHub redirects renamed repos, which once pointed this check at a
    // completely different project (chkt-lite) during testing.
    private const val RELEASES_URL = "https://api.github.com/repos/lightmorphic/chkt/releases/latest"
    const val PROJECT_URL = "https://github.com/lightmorphic/chkt"

    data class UpdateInfo(val version: String, val apkUrl: String?, val notes: String)

    sealed class CheckResult {
        data class UpToDate(val current: String) : CheckResult()
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    /**
     * The version actually on disk, not BuildConfig.VERSION_NAME. Installing
     * an update hands off to Android's installer but doesn't restart this
     * process, so the compiled-in BuildConfig value stays stale until the
     * app is relaunched — reading it from PackageManager instead means a
     * check right after installing correctly sees the new version and
     * won't keep offering the same "update" over and over.
     */
    fun installedVersionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: BuildConfig.VERSION_NAME

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val current = installedVersionName(context)
        try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 404) {
                return@withContext CheckResult.Failed("No releases published yet.")
            }
            if (conn.responseCode != 200) {
                return@withContext CheckResult.Failed("Update server answered HTTP ${conn.responseCode}.")
            }
            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            val json = JSONObject(body)
            val latest = json.optString("tag_name").removePrefix("v")
            if (latest.isBlank()) return@withContext CheckResult.Failed("Couldn't read the latest version.")

            if (!isNewer(latest, current)) {
                return@withContext CheckResult.UpToDate(current)
            }
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            CheckResult.UpdateAvailable(
                UpdateInfo(latest, apkUrl, json.optString("body").take(500))
            )
        } catch (e: Exception) {
            CheckResult.Failed("Couldn't check: ${e.message ?: "no connection"}")
        }
    }

    sealed class DownloadResult {
        data class Ok(val apk: File) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    /** Only ever install what GitHub itself serves, over HTTPS. The URL
     * comes from release JSON, so don't trust it blindly. */
    private fun isGithubHttps(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        val host = uri.host ?: return false
        return host == "github.com" || host.endsWith(".githubusercontent.com")
    }

    /** Follows redirects by hand so the GitHub-over-HTTPS check applies to
     * EVERY hop, not just the first URL — automatic following would happily
     * leave the allowlist on a redirect. */
    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            if (conn.responseCode !in 301..308) return conn
            val next = conn.getHeaderField("Location")
                ?: throw java.io.IOException("Redirect with no destination.")
            conn.disconnect()
            val resolved = java.net.URI(current).resolve(next).toString()
            if (!isGithubHttps(resolved)) {
                throw java.io.IOException("Redirected off GitHub; refusing.")
            }
            current = resolved
        }
        throw java.io.IOException("Too many redirects.")
    }

    /** Downloads the release APK to cache, reporting 0f..1f progress as it goes. */
    suspend fun downloadUpdate(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: return@withContext DownloadResult.Failed("That release has no APK attached.")
        if (!isGithubHttps(url)) {
            return@withContext DownloadResult.Failed("Refusing a download URL that isn't GitHub over HTTPS.")
        }
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "chkt-${info.version}.apk")
            val conn = openFollowingRedirects(url)
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            val total = conn.contentLength
            conn.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var readSoFar = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        readSoFar += n
                        if (total > 0) onProgress((readSoFar.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            DownloadResult.Ok(apk)
        } catch (e: Exception) {
            DownloadResult.Failed("Download failed: ${e.message ?: "unknown error"}")
        }
    }

    /** Hands a downloaded release APK to Android's own installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "org.chkt.app.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    /** Downloads the release APK and hands it to Android's installer in one step. */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): String? =
        when (val result = downloadUpdate(context, info)) {
            is DownloadResult.Ok -> { install(context, result.apk); null }
            is DownloadResult.Failed -> result.message
        }

    /** True when `candidate` is a strictly newer x.y.z than `current`. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split(".", "-").take(3)
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun setAutoCheck(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork("chkt-update-check")
            return
        }
        wm.enqueueUniquePeriodicWork(
            "chkt-update-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = Updater.check(applicationContext)
        if (result is Updater.CheckResult.UpdateAvailable) {
            val launch = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?: return Result.success()
            val open = PendingIntent.getActivity(
                applicationContext, 9001, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("CHKT ${result.info.version} is available")
                .setContentText("Open Settings in CHKT to update with one tap.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext).notify(9001, notification)
            } catch (e: SecurityException) {
                // Notifications off; the Settings card still shows the update.
            }
        }
        return Result.success()
    }
}
