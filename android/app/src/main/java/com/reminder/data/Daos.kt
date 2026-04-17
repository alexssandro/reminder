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
interface OccurrenceDao {
    @Query("""SELECT o.* FROM occurrences o
              INNER JOIN reminders r ON r.id = o.reminderLocalId
              WHERE o.checkedAtUtc IS NULL AND r.pendingDelete = 0
              ORDER BY o.dueAtUtc ASC""")
    fun observePending(): Flow<List<OccurrenceRow>>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun findByLocalId(id: Long): OccurrenceRow?

    @Query("SELECT * FROM occurrences WHERE pendingCreate = 1 OR pendingCheck = 1")
    suspend fun pending(): List<OccurrenceRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(o: OccurrenceRow): Long

    @Update
    suspend fun update(o: OccurrenceRow)

    @Query("DELETE FROM occurrences WHERE reminderLocalId = :reminderLocalId")
    suspend fun deleteByReminder(reminderLocalId: Long)
}
