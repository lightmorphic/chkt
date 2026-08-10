package org.chkt.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val service = Intent(context, AlertService::class.java)
            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, id)
        ContextCompat.startForegroundService(context, service)
    }
}
