package com.reminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromKind(k: ScheduleKind): Int = k.api
    @TypeConverter fun toKind(v: Int): ScheduleKind =
        ScheduleKind.values().first { it.api == v }
}

@Database(
    entities = [ReminderRow::class, OccurrenceRow::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun occurrences(): OccurrenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "reminder.db"
                ).build().also { INSTANCE = it }
            }
    }
}
