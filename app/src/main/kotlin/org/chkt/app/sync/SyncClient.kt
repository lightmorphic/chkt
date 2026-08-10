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
import org.chkt.app.data.ReminderList
import org.chkt.app.data.Repository
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a Chkt Server. One endpoint does the whole job:
 *
 *   POST /api/sync  { since, lists, reminders, logs }  → server changes since `since`
 *
 * Both sides keep every record's updatedAt and deleted tombstones; whoever
 * edited last wins, deletions never resurrect, and completion logs are
 * append-only so nothing is ever silently lost.
 */
class SyncClient(private val context: Context) {

    suspend fun testConnection(serverUrl: String, accessKey: String): String =
        withContext(Dispatchers.IO) {
            if (serverUrl.isBlank()) return@withContext "Enter the server address first."
            if (accessKey.isBlank()) return@withContext "Enter the access key first."
            try {
                val conn = open(serverUrl.trimEnd('/') + "/api/ping", accessKey, "GET")
                val code = conn.responseCode
                when (code) {
                    200 -> "Connected — the server answered."
                    401, 403 -> "The server refused the access key."
                    else -> "The server answered with an error (HTTP $code)."
                }
            } catch (e: Exception) {
                "Couldn't reach the server: ${e.message ?: "unknown error"}"
            }
        }

    /** Returns a plain-language result message; never throws. */
    suspend fun syncNow(): String = withContext(Dispatchers.IO) {
        val repo = Repository(context.applicationContext)
        val config = repo.settings.syncConfig.first()
        if (!config.enabled) return@withContext "Sync is off."
        if (config.serverUrl.isBlank() || config.accessKey.isBlank()) {
            return@withContext "Sync isn't fully set up — server address or access key missing."
        }

        try {
            val since = config.lastSyncAt
            val body = JSONObject().apply {
                put("since", since)
                put("lists", JSONArray().apply {
                    repo.db.lists().changedSince(since).forEach { put(listToJson(it)) }
                })
                put("reminders", JSONArray().apply {
                    repo.db.reminders().changedSince(since).forEach { put(reminderToJson(it)) }
                })
                put("logs", JSONArray().apply {
                    repo.db.logs().changedSince(since).forEach { put(logToJson(it)) }
                })
            }

            val conn = open(config.serverUrl.trimEnd('/') + "/api/sync", config.accessKey, "POST")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != 200) return@withContext "Sync failed (HTTP $code)."

            val response = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() })
            var applied = 0

            val listsArr = response.optJSONArray("lists") ?: JSONArray()
            for (i in 0 until listsArr.length()) {
                if (applyList(repo, listsArr.getJSONObject(i))) applied++
            }
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
            "Synced — $applied changes received."
        } catch (e: Exception) {
            "Sync failed: ${e.message ?: "unknown error"}"
        }
    }

    private suspend fun applyList(repo: Repository, o: JSONObject): Boolean {
        val incoming = ReminderList(
            id = o.getString("id"),
            name = o.getString("name"),
            position = o.optInt("position", 0),
            updatedAt = o.getLong("updatedAt"),
            deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt"),
        )
        val local = repo.db.lists().byId(incoming.id)
        if (local != null && local.updatedAt >= incoming.updatedAt) return false
        repo.db.lists().upsert(incoming)
        return true
    }

    private suspend fun applyReminder(repo: Repository, o: JSONObject): Boolean {
        val incoming = Reminder(
            id = o.getString("id"),
            listId = o.getString("listId"),
            title = o.getString("title"),
            notes = o.optString("notes", ""),
            dueAt = if (o.isNull("dueAt")) null else o.getLong("dueAt"),
            repeatRule = o.optString("repeatRule", ""),
            alertMode = runCatching { AlertMode.valueOf(o.optString("alertMode")) }
                .getOrDefault(AlertMode.RING_AND_SPEAK),
            preTone = o.optBoolean("preTone", false),
            enabled = o.optBoolean("enabled", true),
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
        repo.db.reminders().upsert(incoming)
        // Keep alarms in step with what sync just changed.
        val scheduler = AlarmScheduler(context)
        if (incoming.deletedAt != null || !incoming.enabled) scheduler.cancel(incoming.id)
        else scheduler.schedule(incoming)
        return true
    }

    private fun listToJson(l: ReminderList) = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("position", l.position)
        put("updatedAt", l.updatedAt)
        put("deletedAt", l.deletedAt ?: JSONObject.NULL)
    }

    private fun reminderToJson(r: Reminder) = JSONObject().apply {
        put("id", r.id); put("listId", r.listId)
        put("title", r.title); put("notes", r.notes)
        put("dueAt", r.dueAt ?: JSONObject.NULL)
        put("repeatRule", r.repeatRule)
        put("alertMode", r.alertMode.name)
        put("preTone", r.preTone)
        put("enabled", r.enabled)
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

    private fun open(url: String, accessKey: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Authorization", "Bearer $accessKey")
        conn.setRequestProperty("Content-Type", "application/json")
        if (method == "POST") conn.doOutput = true
        return conn
    }
}
