package org.chkt.app.backup

import android.content.Context
import android.net.Uri
import org.chkt.app.data.AlertMode
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.data.ReminderList
import org.chkt.app.data.Repository
import org.json.JSONArray
import org.json.JSONObject
import org.chkt.app.ui.describeWhen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain-file portability: JSON is the round-trip format (export and import),
 * markdown is a human-readable one-way export. No sync system required.
 */
object ExportImport {
    const val FORMAT_VERSION = 1

    fun exportJson(lists: List<ReminderList>, reminders: List<Reminder>): String {
        val root = JSONObject()
        root.put("app", "chkt")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("lists", JSONArray().apply {
            lists.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("name", l.name); put("position", l.position)
                    put("updatedAt", l.updatedAt)
                })
            }
        })
        root.put("reminders", JSONArray().apply {
            reminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("listId", r.listId)
                    put("title", r.title); put("notes", r.notes)
                    put("dueAt", r.dueAt ?: JSONObject.NULL)
                    put("repeatRule", r.repeatRule)
                    put("alertMode", r.alertMode.name)
                    put("preTone", r.preTone)
                    put("enabled", r.enabled)
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

    fun exportMarkdown(lists: List<ReminderList>, reminders: List<Reminder>): String {
        val sb = StringBuilder("# Chkt reminders\n\n")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        lists.forEach { list ->
            sb.append("## ${list.name}\n\n")
            reminders.filter { it.listId == list.id }.forEach { r ->
                sb.append("- [ ] **${r.title}**")
                val whenText = describeWhen(r)
                if (whenText.isNotBlank()) sb.append(" — $whenText")
                if (r.notes.isNotBlank()) sb.append("\n  ${r.notes}")
                sb.append("\n")
            }
            sb.append("\n")
        }
        sb.append("_Exported ${fmt.format(Instant.now().atZone(ZoneId.systemDefault()))} by Chkt._\n")
        return sb.toString()
    }

    /** Returns the number of reminders imported, or -1 on failure. */
    fun parseJson(raw: String): Pair<List<ReminderList>, List<Reminder>>? = try {
        val root = JSONObject(raw)
        require(root.optString("app") == "chkt")
        val lists = mutableListOf<ReminderList>()
        val listsArr = root.getJSONArray("lists")
        for (i in 0 until listsArr.length()) {
            val o = listsArr.getJSONObject(i)
            lists += ReminderList(
                id = o.getString("id"),
                name = o.getString("name"),
                position = o.optInt("position", 0),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        val reminders = mutableListOf<Reminder>()
        val remArr = root.getJSONArray("reminders")
        for (i in 0 until remArr.length()) {
            val o = remArr.getJSONObject(i)
            reminders += Reminder(
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
                locationTrigger = runCatching { LocationTrigger.valueOf(o.optString("locationTrigger")) }
                    .getOrDefault(LocationTrigger.NONE),
                latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
                longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
                radiusMetres = o.optDouble("radiusMetres", 150.0).toFloat(),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        lists to reminders
    } catch (e: Exception) {
        null
    }

    suspend fun snapshot(repo: Repository): Pair<List<ReminderList>, List<Reminder>> {
        val lists = repo.db.lists().changedSince(0).filter { it.deletedAt == null }
        val reminders = repo.db.reminders().changedSince(0).filter { it.deletedAt == null }
        return lists to reminders
    }

    suspend fun exportJsonToUri(context: Context, repo: Repository, uri: Uri): Boolean = try {
        val (lists, reminders) = snapshot(repo)
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(exportJson(lists, reminders).toByteArray())
        } != null
    } catch (e: Exception) {
        false
    }

    suspend fun exportMarkdownToUri(context: Context, repo: Repository, uri: Uri): Boolean = try {
        val (lists, reminders) = snapshot(repo)
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(exportMarkdown(lists, reminders).toByteArray())
        } != null
    } catch (e: Exception) {
        false
    }

    suspend fun importJsonFromUri(context: Context, repo: Repository, uri: Uri): Int = try {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        val parsed = raw?.let { parseJson(it) }
        if (parsed == null) -1
        else {
            val (lists, reminders) = parsed
            lists.forEach { repo.saveList(it) }
            reminders.forEach { repo.saveReminder(it) }
            reminders.size
        }
    } catch (e: Exception) {
        -1
    }
}
