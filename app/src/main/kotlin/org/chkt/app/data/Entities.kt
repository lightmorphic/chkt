package org.chkt.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Primary keys are UUID strings so records created on different devices
 * can merge during sync without id collisions.
 */
@Entity(tableName = "lists")
data class ReminderList(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val position: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

enum class AlertMode { RING_AND_SPEAK, RING_ONLY, SPEAK_ONLY, NOTIFY_ONLY }

enum class LocationTrigger { NONE, ARRIVE, LEAVE }

@Entity(
    tableName = "reminders",
    indices = [Index("listId"), Index("dueAt")],
)
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val title: String,
    val notes: String = "",
    /** Next time this reminder should fire, epoch millis. Null for location-only reminders. */
    val dueAt: Long?,
    /** Repeat rule string, see [org.chkt.app.domain.RepeatRule]. Empty = one-off. */
    val repeatRule: String = "",
    val alertMode: AlertMode = AlertMode.RING_AND_SPEAK,
    /** Play a short tone before the spoken text. */
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
