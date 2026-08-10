package org.chkt.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE deletedAt IS NULL ORDER BY position, name")
    fun observeAll(): Flow<List<ReminderList>>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun byId(id: String): ReminderList?

    @Upsert
    suspend fun upsert(list: ReminderList)

    @Query("UPDATE lists SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long = System.currentTimeMillis())

    @Query("SELECT * FROM lists WHERE updatedAt > :since")
    suspend fun changedSince(since: Long): List<ReminderList>

    @Query("SELECT COUNT(*) FROM lists WHERE deletedAt IS NULL")
    suspend fun count(): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE listId = :listId AND deletedAt IS NULL ORDER BY dueAt IS NULL, dueAt")
    fun observeForList(listId: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL AND enabled = 1 ORDER BY dueAt IS NULL, dueAt")
    fun observeActive(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: String): Reminder?

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL AND enabled = 1 AND (dueAt IS NOT NULL OR snoozedUntil IS NOT NULL)")
    suspend fun allSchedulable(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL AND enabled = 1 AND locationTrigger != 'NONE'")
    suspend fun allLocationBased(): List<Reminder>

    @Upsert
    suspend fun upsert(reminder: Reminder)

    @Query("UPDATE reminders SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE updatedAt > :since")
    suspend fun changedSince(since: Long): List<Reminder>
}

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: CompletionLog)

    @Query("SELECT * FROM completion_log WHERE at >= :since ORDER BY at DESC")
    fun observeSince(since: Long): Flow<List<CompletionLog>>

    @Query("SELECT * FROM completion_log WHERE at > :since")
    suspend fun changedSince(since: Long): List<CompletionLog>

    @Query("SELECT COUNT(*) FROM completion_log WHERE action = :action AND at >= :since")
    suspend fun countSince(action: LogAction, since: Long): Int
}
