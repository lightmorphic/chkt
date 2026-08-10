package org.chkt.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.chkt.app.data.Repository

/**
 * Alarms don't survive a reboot, a time change, or an app update on their own —
 * this receiver re-arms every one of them whenever any of those happen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Repository(context.applicationContext).rescheduleAll()
            } finally {
                result.finish()
            }
        }
    }
}
