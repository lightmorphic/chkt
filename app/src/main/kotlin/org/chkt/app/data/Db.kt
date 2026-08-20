package org.chkt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun alertModeToString(v: AlertMode): String = v.name
    @TypeConverter fun stringToAlertMode(v: String): AlertMode = AlertMode.fromStored(v)
    @TypeConverter fun locationTriggerToString(v: LocationTrigger): String = v.name
    @TypeConverter fun stringToLocationTrigger(v: String): LocationTrigger = LocationTrigger.valueOf(v)
    @TypeConverter fun logActionToString(v: LogAction): String = v.name
    @TypeConverter fun stringToLogAction(v: String): LogAction = LogAction.valueOf(v)
}

@Database(
    entities = [Reminder::class, CompletionLog::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChktDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun logs(): LogDao

    companion object {
        @Volatile private var instance: ChktDatabase? = null

        fun get(context: Context): ChktDatabase =
            instance ?: synchronized(this) {
                // No destructive-migration fallback: shipped installs exist,
                // so any schema bump MUST come with a Migration (schemas are
                // exported to app/schemas for writing them). A missing one
                // fails loudly instead of silently wiping every reminder.
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChktDatabase::class.java,
                    "chkt.db",
                )
                    .build().also { instance = it }
            }
    }
}
