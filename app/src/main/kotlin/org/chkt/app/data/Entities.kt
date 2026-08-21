package org.chkt.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class AlertMode {
    NOTIFY_AND_SPEAK, SPEAK_ONLY, NOTIFY_ONLY;

    companion object {
        /** Accepts old stored names from before ringing was removed as an
         * alert component, so existing reminders don't crash or reset. */
        fun fromStored(v: String): AlertMode = when (v) {
            "RING_AND_SPEAK" -> NOTIFY_AND_SPEAK
            "RING_ONLY" -> NOTIFY_ONLY
            else -> runCatching { valueOf(v) }.getOrDefault(NOTIFY_AND_SPEAK)
        }
    }
}

enum class LocationTrigger { NONE, ARRIVE, LEAVE }

/**
 * Primary keys are UUID strings so records created on different devices
 * can merge during sync without id collisions.
 */
@Entity(
    tableName = "reminders",
    indices = [Index("dueAt")],
)
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Free-form tags, comma separated. Empty = untagged. */
    val tags: String = "",
    val title: String,
    val notes: String = "",
    /** Next time this reminder should fire, epoch millis. Null for location-only reminders. */
    val dueAt: Long?,
    /** How long the thing itself takes, in minutes. 0 — the default — is a
     * point in time. It changes nothing about the alert; it exists so a
     * reminder published to a calendar has a sensible length there. */
    val durationMinutes: Int = 0,
    /** Repeat rule string, see [org.chkt.app.domain.RepeatRule]. Empty = one-off. */
    val repeatRule: String = "",
    val alertMode: AlertMode = AlertMode.NOTIFY_AND_SPEAK,
    /** No longer settable in the UI; kept only so existing rows don't need
     * a destructive schema migration. */
    val preTone: Boolean = false,
    val enabled: Boolean = true,
    /** Vibrate when the alert fires. */
    val vibrate: Boolean = true,
    /** If true this reminder stays quiet during Do Not Disturb; if false it cuts through. */
    val respectDnd: Boolean = false,
    /** Re-alert every this many minutes until answered. 0 = alert once only. */
    val nagIntervalMinutes: Int = 0,
    /** Stop re-alerting this many minutes after the first alert. */
    val nagStopAfterMinutes: Int = 60,
    /** When the current occurrence started nagging; null when not mid-nag. */
    val nagStartedAt: Long? = null,
    /** Remove the reminder entirely once it has been answered or dismissed. */
    val deleteAfterDismissed: Boolean = false,
    /** When snoozed, the temporary fire time; cleared after firing. */
    val snoozedUntil: Long? = null,
    val locationTrigger: LocationTrigger = LocationTrigger.NONE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMetres: Float = 150f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Tombstone for sync: non-null means deleted, kept so other devices learn of it. */
    val deletedAt: Long? = null,
)

enum class LogAction { DONE, MISSED, SNOOZED }

@Entity(
    tableName = "completion_log",
    indices = [Index("reminderId"), Index("at")],
)
data class CompletionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reminderId: String,
    /** The occurrence this entry refers to (the reminder's dueAt when it fired). */
    val dueAt: Long,
    val action: LogAction,
    val at: Long = System.currentTimeMillis(),
)
