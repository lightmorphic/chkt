package org.chkt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.chkt.app.data.Repository
import org.chkt.app.sync.SyncClient
import org.chkt.app.ui.ChktApp
import org.chkt.app.ui.theme.ChktTheme

class MainActivity : ComponentActivity() {
    // Outlives any single onStart so a sync isn't cancelled by a quick
    // rotate; app-lifetime leak-free because the work is short and self-
    // contained.
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChktTheme {
                ChktApp()
            }
        }
    }

    /**
     * Opening the app is the strongest "show me what's new" signal there
     * is, and the background worker only runs every 15 minutes — so sync
     * now, quietly. Anything a calendar app or the web UI added appears
     * within seconds of picking the phone up. Debounced so hopping between
     * apps doesn't hammer the server; failures stay silent here, the
     * pull-down gesture is the loud version.
     */
    override fun onStart() {
        super.onStart()
        syncScope.launch {
            try {
                val repo = Repository(applicationContext)
                val config = repo.settings.syncConfig.first()
                if (!config.enabled) return@launch
                // Re-assert the periodic worker so interval changes reach
                // installs that set sync up under an older schedule.
                org.chkt.app.sync.SyncScheduler.ensureScheduled(applicationContext)
                if (System.currentTimeMillis() - config.lastSyncAt < 60_000) return@launch
                SyncClient(applicationContext).syncNow()
            } catch (e: Exception) {
                // Quiet by design: the next look at the list will try again.
            }
        }
    }
}
