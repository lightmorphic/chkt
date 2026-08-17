package org.chkt.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.store by preferencesDataStore(name = "settings")

data class QuietHours(
    val enabled: Boolean = false,
    /** Minutes since midnight. Start may be after end (spans midnight, e.g. 22:00–07:00). */
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 7 * 60,
) {
    fun contains(time: LocalTime): Boolean {
        if (!enabled) return false
        val t = time.hour * 60 + time.minute
        return if (startMinutes <= endMinutes) t in startMinutes until endMinutes
        else t >= startMinutes || t < endMinutes
    }
}

data class SyncConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val accessKey: String = "",
    val lastSyncAt: Long = 0,
)

class AppSettings(private val context: Context) {
    private object Keys {
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val backupFolder = stringPreferencesKey("backup_folder")
        val backupEnabled = booleanPreferencesKey("backup_enabled")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val syncServer = stringPreferencesKey("sync_server")
        val syncKey = stringPreferencesKey("sync_key")
        val syncLast = longPreferencesKey("sync_last")
        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
    }

    /** Daily update check; opt-in, off by default. */
    val autoUpdateCheck: Flow<Boolean> = context.store.data.map { it[Keys.autoUpdateCheck] ?: false }

    suspend fun setAutoUpdateCheck(enabled: Boolean) = context.store.edit {
        it[Keys.autoUpdateCheck] = enabled
    }

    val quietHours: Flow<QuietHours> = context.store.data.map { p ->
        QuietHours(
            enabled = p[Keys.quietEnabled] ?: false,
            startMinutes = p[Keys.quietStart] ?: 22 * 60,
            endMinutes = p[Keys.quietEnd] ?: 7 * 60,
        )
    }

    val backupFolder: Flow<String?> = context.store.data.map { it[Keys.backupFolder] }
    val backupEnabled: Flow<Boolean> = context.store.data.map { it[Keys.backupEnabled] ?: false }

    val syncConfig: Flow<SyncConfig> = context.store.data.map { p ->
        SyncConfig(
            enabled = p[Keys.syncEnabled] ?: false,
            serverUrl = p[Keys.syncServer] ?: "",
            accessKey = p[Keys.syncKey] ?: "",
            lastSyncAt = p[Keys.syncLast] ?: 0,
        )
    }


    suspend fun quietHoursNow(): QuietHours = quietHours.first()

    suspend fun setQuietHours(q: QuietHours) = context.store.edit {
        it[Keys.quietEnabled] = q.enabled
        it[Keys.quietStart] = q.startMinutes
        it[Keys.quietEnd] = q.endMinutes
    }

    suspend fun setBackup(enabled: Boolean, folderUri: String?) = context.store.edit {
        it[Keys.backupEnabled] = enabled
        if (folderUri != null) it[Keys.backupFolder] = folderUri
    }

    suspend fun setSync(config: SyncConfig) = context.store.edit {
        it[Keys.syncEnabled] = config.enabled
        it[Keys.syncServer] = config.serverUrl
        it[Keys.syncKey] = config.accessKey
        it[Keys.syncLast] = config.lastSyncAt
    }

    suspend fun setLastSync(at: Long) = context.store.edit { it[Keys.syncLast] = at }

}
