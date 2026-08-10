package org.chkt.app.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.chkt.app.alarm.AlarmScheduler
import org.chkt.app.alarm.AlertService
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Repository

/**
 * Location reminders use the platform's proximity alerts (LocationManager),
 * not Google's fused geofencing, so they work on de-Googled phones. The
 * trade-off is honest battery use: proximity alerts poll location, so we only
 * register alerts while location reminders actually exist.
 */
object LocationReminders {

    @SuppressLint("MissingPermission")
    suspend fun registerAll(context: Context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val reminders = Repository(context).db.reminders().allLocationBased()
        reminders.forEach { r ->
            val lat = r.latitude ?: return@forEach
            val lon = r.longitude ?: return@forEach
            try {
                lm.addProximityAlert(lat, lon, r.radiusMetres, -1, pendingIntent(context, r.id))
            } catch (e: SecurityException) {
                // Location permission not granted; the settings screen surfaces this.
            }
        }
    }

    fun unregister(context: Context, reminderId: String) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeProximityAlert(pendingIntent(context, reminderId))
        } catch (e: SecurityException) {
            // Nothing to remove without permission.
        }
    }

    private fun pendingIntent(context: Context, reminderId: String): PendingIntent {
        val intent = Intent(context, ProximityReceiver::class.java)
            .setAction("org.chkt.app.PROXIMITY")
            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            .setData(android.net.Uri.parse("chkt://proximity/$reminderId"))
        return PendingIntent.getBroadcast(
            context, ("proximity" + reminderId).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

class ProximityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = Repository(context.applicationContext).db.reminders().byId(id)
                val wants = when (reminder?.locationTrigger) {
                    LocationTrigger.ARRIVE -> entering
                    LocationTrigger.LEAVE -> !entering
                    else -> false
                }
                if (wants && reminder != null && reminder.enabled && reminder.deletedAt == null) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AlertService::class.java)
                            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, id),
                    )
                }
            } finally {
                result.finish()
            }
        }
    }
}
