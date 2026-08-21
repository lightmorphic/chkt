package org.chkt.app.alarm

import android.content.Context
import org.json.JSONArray
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A small ring buffer of what recently-fired alerts actually did — chime
 * started, finished or failed, voice spoken — readable under Settings.
 *
 * Exists because "the sound didn't play" on someone's phone is otherwise
 * undebuggable from the outside: alert audio has no visible trace, and the
 * failure is usually specific to one device's ROM, engine or sound choice.
 */
object AlertLog {
    private const val PREFS = "chkt_alert_log"
    private const val KEY = "lines"
    private const val MAX_LINES = 60
    private val stamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun log(context: Context, line: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            val lines = read(context).toMutableList()
            lines += LocalTime.now().format(stamp) + "  " + line
            while (lines.size > MAX_LINES) lines.removeAt(0)
            prefs.edit().putString(KEY, JSONArray(lines).toString()).apply()
        }
    }

    fun read(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
