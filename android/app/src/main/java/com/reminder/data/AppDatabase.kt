package com.reminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun fromKind(k: ScheduleKind): Int = k.api
    @TypeConverter fun toKind(v: Int): ScheduleKind =
        ScheduleKind.values().first { it.api == v }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminder_overrides` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `reminderLocalId` INTEGER NOT NULL,
                `localDate` TEXT NOT NULL,
                `minuteOfDay` INTEGER NOT NULL,
                `createdAtUtc` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_overrides_reminderLocalId_localDate` " +
                "ON `reminder_overrides` (`reminderLocalId`, `localDate`)"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `weeklyDaysMask` INTEGER")
    }
}

@Database(
    entities = [ReminderRow::class, OccurrenceRow::class, ReminderOverrideRow::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun overrides(): ReminderOverrideDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "reminder.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
