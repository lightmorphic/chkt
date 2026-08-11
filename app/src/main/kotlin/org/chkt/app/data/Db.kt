package org.chkt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun alertModeToString(v: AlertMode): String = v.name
    @TypeConverter fun stringToAlertMode(v: String): AlertMode = AlertMode.valueOf(v)
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
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChktDatabase::class.java,
                    "chkt.db",
                )
                    // Pre-release only: no shipped installs exist, so schema
                    // changes rebuild the database instead of migrating.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
