package org.chkt.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.chkt.app.data.Repository

/**
 * Alarms don't survive a reboot, a time change, or an app update on their own,
 * this receiver re-arms every one of them whenever any of those happen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported (the system needs to reach it), so ignore anything except
        // the actions we registered for — not explicit invocations by other
        // apps.
        if (intent.action !in HANDLED_ACTIONS) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Repository(context.applicationContext).rescheduleAll()
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
