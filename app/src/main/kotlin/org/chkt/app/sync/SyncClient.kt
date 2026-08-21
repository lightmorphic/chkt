package org.chkt.app.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.chkt.app.alarm.AlarmScheduler
import org.chkt.app.data.AlertMode
import org.chkt.app.data.CompletionLog
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.LogAction
import org.chkt.app.data.Reminder
import org.chkt.app.data.Repository
import org.chkt.app.location.LocationReminders
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a CHKT Server. One endpoint does the whole job:
 *
 *   POST /api/sync  { since, reminders, logs }  → server changes since `since`
 *
 * Both sides keep every record's updatedAt and deleted tombstones; whoever
 * edited last wins, deletions never resurrect, and completion logs are
 * append-only so nothing is ever silently lost.
 */
class SyncClient(private val context: Context) {

    data class ConnectionTest(val ok: Boolean, val message: String)

    suspend fun testConnection(serverUrl: String, accessKey: String): ConnectionTest =
        withContext(Dispatchers.IO) {
            if (serverUrl.isBlank()) return@withContext ConnectionTest(false, "Enter the server address first.")
            if (accessKey.isBlank()) return@withContext ConnectionTest(false, "Enter the access key first.")
            try {
                val conn = open(serverUrl.trimEnd('/') + "/api/ping", accessKey, "GET")
                val code = conn.responseCode
                when (code) {
                    200 -> ConnectionTest(true, "Connected, the server answered.")
                    401, 403 -> ConnectionTest(false, "The server refused the access key.")
                    else -> ConnectionTest(false, "The server answered with an error (HTTP $code).")
                }
            } catch (e: Exception) {
                ConnectionTest(false, "Couldn't reach the server: ${e.message ?: "unknown error"}")
            }
        }

    /** Returns a plain-language result message; never throws. */
    suspend fun syncNow(): String = withContext(Dispatchers.IO) {
        val repo = Repository(context.applicationContext)
        val config = repo.settings.syncConfig.first()
        if (!config.enabled) return@withContext "Sync is off."
        if (config.serverUrl.isBlank() || config.accessKey.isBlank()) {
            return@withContext "Sync isn't fully set up. Server address or access key missing."
        }

        try {
            val since = config.lastSyncAt
            // The watermark is the SERVER's clock but updatedAt is stamped
            // with this phone's; if the phone runs behind, an edit made just
            // after a sync could fall below the watermark and never upload.
            // Querying with a margin closes that window — re-sending an
            // already-synced record is harmless (newest-wins on both ends).
            val uploadSince = (since - CLOCK_SKEW_MARGIN_MS).coerceAtLeast(0)
            val body = JSONObject().apply {
                put("since", since)
                put("reminders", JSONArray().apply {
                    repo.db.reminders().changedSince(uploadSince).forEach { put(reminderToJson(it)) }
                })
                put("logs", JSONArray().apply {
                    repo.db.logs().changedSince(uploadSince).forEach { put(logToJson(it)) }
                })
            }

            val conn = open(config.serverUrl.trimEnd('/') + "/api/sync", config.accessKey, "POST")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != 200) return@withContext "Sync failed (HTTP $code)."

            // 20 MB is orders of magnitude past any real reminder set; an
            // unbounded read lets a haywire server OOM the app instead.
            val response = JSONObject(conn.inputStream.use { readCapped(it, 20 * 1024 * 1024) })
            var applied = 0

            val remArr = response.optJSONArray("reminders") ?: JSONArray()
            for (i in 0 until remArr.length()) {
                if (applyReminder(repo, remArr.getJSONObject(i))) applied++
            }
            val logsArr = response.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until logsArr.length()) {
                val o = logsArr.getJSONObject(i)
                repo.db.logs().insert(
                    CompletionLog(
                        id = o.getString("id"),
                        reminderId = o.getString("reminderId"),
                        dueAt = o.getLong("dueAt"),
                        action = runCatching { LogAction.valueOf(o.getString("action")) }
                            .getOrDefault(LogAction.DONE),
                        at = o.getLong("at"),
                    )
                )
            }

            repo.settings.setLastSync(response.optLong("now", System.currentTimeMillis()))
            "Synced. $applied changes received."
        } catch (e: Exception) {
            "Sync failed: ${e.message ?: "unknown error"}"
        }
    }

    private suspend fun applyReminder(repo: Repository, o: JSONObject): Boolean {
        val incoming = Reminder(
            id = o.getString("id"),
            tags = o.optString("tags", ""),
            title = o.getString("title"),
            notes = o.optString("notes", ""),
            dueAt = if (o.isNull("dueAt")) null else o.getLong("dueAt"),
            durationMinutes = o.optInt("durationMinutes", 0),
            repeatRule = o.optString("repeatRule", ""),
            alertMode = AlertMode.fromStored(o.optString("alertMode")),
            preTone = o.optBoolean("preTone", false),
            enabled = o.optBoolean("enabled", true),
            vibrate = o.optBoolean("vibrate", true),
            respectDnd = o.optBoolean("respectDnd", false),
            nagIntervalMinutes = o.optInt("nagIntervalMinutes", 0),
            nagStopAfterMinutes = o.optInt("nagStopAfterMinutes", 60),
            deleteAfterDismissed = o.optBoolean("deleteAfterDismissed", false),
            snoozedUntil = if (o.isNull("snoozedUntil")) null else o.getLong("snoozedUntil"),
            locationTrigger = runCatching { LocationTrigger.valueOf(o.optString("locationTrigger")) }
                .getOrDefault(LocationTrigger.NONE),
            latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
            longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
            radiusMetres = o.optDouble("radiusMetres", 150.0).toFloat(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.getLong("updatedAt"),
            deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt"),
        )
        val local = repo.db.reminders().byId(incoming.id)
        if (local != null && local.updatedAt >= incoming.updatedAt) return false
        // nagStartedAt tracks this device's own in-progress re-alert cycle;
        // the server never sends it (it isn't in the JSON contract), so a
        // sync merge must never clobber it with the null default.
        val toStore = incoming.copy(nagStartedAt = local?.nagStartedAt)
        repo.db.reminders().upsert(toStore)
        // Keep alarms in step with what sync just changed.
        val scheduler = AlarmScheduler(context)
        if (toStore.deletedAt != null || !toStore.enabled) scheduler.cancel(toStore.id)
        else scheduler.schedule(toStore)
        // Same for proximity alerts: a location reminder created or cleared
        // on the web side must (un)register here too, not wait for the next
        // reboot's rescheduleAll.
        if (toStore.deletedAt != null || toStore.locationTrigger == LocationTrigger.NONE) {
            LocationReminders.unregister(context, toStore.id)
        } else {
            LocationReminders.registerAll(context)
        }
        return true
    }

    private fun reminderToJson(r: Reminder) = JSONObject().apply {
        put("id", r.id); put("tags", r.tags)
        put("title", r.title); put("notes", r.notes)
        put("dueAt", r.dueAt ?: JSONObject.NULL)
        put("durationMinutes", r.durationMinutes)
        put("repeatRule", r.repeatRule)
        put("alertMode", r.alertMode.name)
        put("preTone", r.preTone)
        put("enabled", r.enabled)
        put("vibrate", r.vibrate)
        put("respectDnd", r.respectDnd)
        put("nagIntervalMinutes", r.nagIntervalMinutes)
        put("nagStopAfterMinutes", r.nagStopAfterMinutes)
        put("deleteAfterDismissed", r.deleteAfterDismissed)
        put("snoozedUntil", r.snoozedUntil ?: JSONObject.NULL)
        put("locationTrigger", r.locationTrigger.name)
        put("latitude", r.latitude ?: JSONObject.NULL)
        put("longitude", r.longitude ?: JSONObject.NULL)
        put("radiusMetres", r.radiusMetres.toDouble())
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        put("deletedAt", r.deletedAt ?: JSONObject.NULL)
    }

    private fun logToJson(l: CompletionLog) = JSONObject().apply {
        put("id", l.id); put("reminderId", l.reminderId)
        put("dueAt", l.dueAt); put("action", l.action.name); put("at", l.at)
    }

    private fun readCapped(input: java.io.InputStream, maxBytes: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= maxBytes) { "The server response is unreasonably large." }
        }
        return out.toByteArray().decodeToString()
    }

    private fun open(url: String, accessKey: String, method: String): HttpURLConnection {
        // Only web schemes carry this bearer key anywhere sensible; anything
        // else (file://, content://) is a typo or worse, and would otherwise
        // surface as an obscure ClassCastException.
        val scheme = runCatching { java.net.URI(url.trim()).scheme }.getOrNull()
        require(scheme == "http" || scheme == "https") { "The server address must start with http:// or https://." }
        val conn = URL(url.trim()).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Authorization", "Bearer $accessKey")
        conn.setRequestProperty("Content-Type", "application/json")
        if (method == "POST") conn.doOutput = true
        return conn
    }

    private companion object {
        const val CLOCK_SKEW_MARGIN_MS = 5 * 60_000L
    }
}
