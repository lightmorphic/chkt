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
