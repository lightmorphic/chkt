package org.chkt.app.backup

import android.content.Context
import android.net.Uri
import org.chkt.app.data.AlertMode
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.data.Repository
import org.chkt.app.ui.describeWhen
import org.chkt.app.ui.tagList
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain-file portability: JSON is the round-trip format (export and import),
 * markdown is a human-readable one-way export. Format version 2 uses tags;
 * version 1 files (which had lists) import cleanly, list names become tags.
 */
object ExportImport {
    const val FORMAT_VERSION = 2

    fun exportJson(reminders: List<Reminder>): String {
        val root = JSONObject()
        root.put("app", "chkt")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("reminders", JSONArray().apply {
            reminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("tags", r.tags)
                    put("title", r.title); put("notes", r.notes)
                    put("dueAt", r.dueAt ?: JSONObject.NULL)
                    put("repeatRule", r.repeatRule)
                    put("alertMode", r.alertMode.name)
                    put("preTone", r.preTone)
                    put("enabled", r.enabled)
                    put("vibrate", r.vibrate)
                    put("respectDnd", r.respectDnd)
                    put("nagIntervalMinutes", r.nagIntervalMinutes)
                    put("nagStopAfterMinutes", r.nagStopAfterMinutes)
                    put("deleteAfterDismissed", r.deleteAfterDismissed)
                    put("locationTrigger", r.locationTrigger.name)
                    put("latitude", r.latitude ?: JSONObject.NULL)
                    put("longitude", r.longitude ?: JSONObject.NULL)
                    put("radiusMetres", r.radiusMetres.toDouble())
                    put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                })
            }
        })
        return root.toString(2)
    }

    fun exportMarkdown(reminders: List<Reminder>): String {
        val sb = StringBuilder("# Chkt reminders\n\n")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        reminders.forEach { r ->
            sb.append("- [ ] **${r.title}**")
            val whenText = describeWhen(r)
            if (whenText.isNotBlank()) sb.append(", $whenText")
            val tags = r.tagList()
            if (tags.isNotEmpty()) sb.append("  ").append(tags.joinToString(" ") { "#$it" })
            if (r.notes.isNotBlank()) sb.append("\n  ${r.notes}")
            sb.append("\n")
        }
        sb.append("\n_Exported ${fmt.format(Instant.now().atZone(ZoneId.systemDefault()))} by Chkt._\n")
        return sb.toString()
    }

    /** Parses a v1 or v2 export. Returns null when the file isn't Chkt's. */
    fun parseJson(raw: String): List<Reminder>? = try {
        val root = JSONObject(raw)
        require(root.optString("app") == "chkt")

        // v1 had lists; carry their names over as tags.
        val listNames = mutableMapOf<String, String>()
        root.optJSONArray("lists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                listNames[o.getString("id")] = o.optString("name", "")
            }
        }

        val reminders = mutableListOf<Reminder>()
        val remArr = root.getJSONArray("reminders")
        for (i in 0 until remArr.length()) {
            val o = remArr.getJSONObject(i)
            val tags = when {
                o.has("tags") -> o.optString("tags", "")
                else -> listNames[o.optString("listId")] ?: ""
            }
            reminders += Reminder(
                id = o.getString("id"),
                tags = tags,
                title = o.getString("title"),
                notes = o.optString("notes", ""),
                dueAt = if (o.isNull("dueAt")) null else o.getLong("dueAt"),
                repeatRule = o.optString("repeatRule", ""),
                alertMode = runCatching { AlertMode.valueOf(o.optString("alertMode")) }
                    .getOrDefault(AlertMode.RING_AND_SPEAK),
                preTone = o.optBoolean("preTone", false),
                enabled = o.optBoolean("enabled", true),
                vibrate = o.optBoolean("vibrate", true),
                respectDnd = o.optBoolean("respectDnd", false),
                nagIntervalMinutes = o.optInt("nagIntervalMinutes", 0),
                nagStopAfterMinutes = o.optInt("nagStopAfterMinutes", 60),
                deleteAfterDismissed = o.optBoolean("deleteAfterDismissed", false),
                locationTrigger = runCatching { LocationTrigger.valueOf(o.optString("locationTrigger")) }
                    .getOrDefault(LocationTrigger.NONE),
                latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
                longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
                radiusMetres = o.optDouble("radiusMetres", 150.0).toFloat(),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        reminders
    } catch (e: Exception) {
        null
    }

    suspend fun snapshot(repo: Repository): List<Reminder> =
        repo.db.reminders().changedSince(0).filter { it.deletedAt == null }

    suspend fun exportJsonToUri(context: Context, repo: Repository, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(exportJson(snapshot(repo)).toByteArray())
        } != null
    } catch (e: Exception) {
        false
    }

    suspend fun exportMarkdownToUri(context: Context, repo: Repository, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(exportMarkdown(snapshot(repo)).toByteArray())
        } != null
    } catch (e: Exception) {
        false
    }

    suspend fun importJsonFromUri(context: Context, repo: Repository, uri: Uri): Int = try {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        val reminders = raw?.let { parseJson(it) }
        if (reminders == null) -1
        else {
            reminders.forEach { repo.saveReminder(it) }
            reminders.size
        }
    } catch (e: Exception) {
        -1
    }
}
