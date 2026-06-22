package com.reminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE pendingDelete = 0 ORDER BY isActive DESC, id ASC")
    fun observeAll(): Flow<List<ReminderRow>>

    @Query("SELECT * FROM reminders WHERE pendingDelete = 0 AND isActive = 1")
    suspend fun activeList(): List<ReminderRow>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findByLocalId(id: Long): ReminderRow?

    @Query("SELECT * FROM reminders WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Int): ReminderRow?

    @Query("SELECT * FROM reminders WHERE pendingCreate = 1 OR pendingUpdate = 1 OR pendingDelete = 1")
    suspend fun pending(): List<ReminderRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: ReminderRow): Long

    @Update
    suspend fun update(r: ReminderRow)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteByLocalId(id: Long)
}

@Dao
interface ReminderOverrideDao {
    @Query("SELECT * FROM reminder_overrides WHERE reminderLocalId = :reminderLocalId AND localDate = :localDate LIMIT 1")
    suspend fun findFor(reminderLocalId: Long, localDate: String): ReminderOverrideRow?

    @Query("SELECT * FROM reminder_overrides WHERE reminderLocalId = :reminderLocalId ORDER BY localDate ASC")
    fun observeForReminder(reminderLocalId: Long): Flow<List<ReminderOverrideRow>>

    @Query("SELECT * FROM reminder_overrides WHERE localDate >= :fromDate ORDER BY localDate ASC")
    fun observeFrom(fromDate: String): Flow<List<ReminderOverrideRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReminderOverrideRow): Long

    @Query("DELETE FROM reminder_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminder_overrides WHERE reminderLocalId = :reminderLocalId")
    suspend fun deleteByReminder(reminderLocalId: Long)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items ORDER BY reminderLocalId ASC, position ASC")
    fun observeItems(): Flow<List<ChecklistItemRow>>

    @Query("SELECT * FROM checklist_items WHERE reminderLocalId = :reminderLocalId ORDER BY position ASC")
    suspend fun itemsFor(reminderLocalId: Long): List<ChecklistItemRow>

    @Insert
    suspend fun insertItem(row: ChecklistItemRow): Long

    @Query("DELETE FROM checklist_items WHERE reminderLocalId = :reminderLocalId")
    suspend fun deleteItemsForReminder(reminderLocalId: Long)

    @Query("SELECT * FROM checklist_checks WHERE localDate = :localDate")
    fun observeChecksOn(localDate: String): Flow<List<ChecklistCheckRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheck(row: ChecklistCheckRow): Long

    @Query("DELETE FROM checklist_checks WHERE checklistItemLocalId = :itemId AND localDate = :localDate")
    suspend fun deleteCheck(itemId: Long, localDate: String)

    @Query("DELETE FROM checklist_checks WHERE checklistItemLocalId = :itemId")
    suspend fun deleteChecksForItem(itemId: Long)
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<WishlistItemRow>>

    @Query("SELECT * FROM wishlist_items ORDER BY position DESC LIMIT 1")
    suspend fun last(): WishlistItemRow?

    @Insert
    suspend fun insert(row: WishlistItemRow): Long

    @Update
    suspend fun update(row: WishlistItemRow)

    @Query("UPDATE wishlist_items SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Mark bought ([boughtAtUtc] = now) or restore to the wishlist ([boughtAtUtc] = null). */
    @Query("UPDATE wishlist_items SET boughtAtUtc = :boughtAtUtc WHERE id = :id")
    suspend fun setBought(id: Long, boughtAtUtc: Long?)

    @Query("DELETE FROM wishlist_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface OccurrenceDao {
    @Query("""SELECT o.* FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NULL AND r.pendingDelete = 0
              ORDER BY o.dueAtUtc ASC""")
    fun observePending(): Flow<List<OccurrenceRow>>

    @Query("""SELECT o.* FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NOT NULL AND o.checkedAtUtc >= :sinceUtc AND r.pendingDelete = 0
              ORDER BY o.checkedAtUtc DESC""")
    fun observeCheckedSince(sinceUtc: Long): Flow<List<OccurrenceRow>>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun findByLocalId(id: Long): OccurrenceRow?

    @Query("SELECT * FROM occurrences WHERE reminderLocalId = :reminderLocalId AND checkedAtUtc IS NULL")
    suspend fun uncheckedForReminder(reminderLocalId: Long): List<OccurrenceRow>

    @Query("""SELECT o.* FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NOT NULL AND o.checkedAtUtc >= :sinceUtc AND r.pendingDelete = 0""")
    suspend fun checkedSince(sinceUtc: Long): List<OccurrenceRow>

    /** Reminder ids that have ever been checked off (any day). Used for the permanent
     *  "done forever" semantics of Anytime reminders, which never reset. */
    @Query("""SELECT DISTINCT o.reminderLocalId FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NOT NULL AND r.pendingDelete = 0""")
    fun observeEverCheckedReminderIds(): Flow<List<Long>>

    @Query("""SELECT DISTINCT o.reminderLocalId FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NOT NULL AND r.pendingDelete = 0""")
    suspend fun everCheckedReminderIds(): List<Long>

    @Query("SELECT * FROM occurrences WHERE pendingCreate = 1 OR pendingCheck = 1")
    suspend fun pending(): List<OccurrenceRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(o: OccurrenceRow): Long

    @Update
    suspend fun update(o: OccurrenceRow)

    @Query("DELETE FROM occurrences WHERE reminderLocalId = :reminderLocalId")
    suspend fun deleteByReminder(reminderLocalId: Long)

    @Query("DELETE FROM occurrences WHERE id = :id")
    suspend fun deleteByLocalId(id: Long)
}
