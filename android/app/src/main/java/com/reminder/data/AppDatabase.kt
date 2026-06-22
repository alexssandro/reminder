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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_items` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `reminderLocalId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `createdAtUtc` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_checklist_items_reminderLocalId` " +
                "ON `checklist_items` (`reminderLocalId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_checks` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `checklistItemLocalId` INTEGER NOT NULL,
                `localDate` TEXT NOT NULL,
                `checkedAtUtc` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_checks_checklistItemLocalId_localDate` " +
                "ON `checklist_checks` (`checklistItemLocalId`, `localDate`)"
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `monthlyDayOfMonth` INTEGER")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wishlist_items` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `bestPrice` REAL,
                `store` TEXT,
                `position` INTEGER NOT NULL,
                `createdAtUtc` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wishlist_items` ADD COLUMN `boughtAtUtc` INTEGER")
    }
}

@Database(
    entities = [
        ReminderRow::class, OccurrenceRow::class, ReminderOverrideRow::class,
        ChecklistItemRow::class, ChecklistCheckRow::class, WishlistItemRow::class,
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun overrides(): ReminderOverrideDao
    abstract fun checklist(): ChecklistDao
    abstract fun wishlist(): WishlistDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "reminder.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7,
                    )
                    .build().also { INSTANCE = it }
            }
    }
}
