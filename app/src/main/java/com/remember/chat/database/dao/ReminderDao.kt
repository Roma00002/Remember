package com.remember.chat.database.dao

import androidx.room.*
import com.remember.chat.database.entities.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Reminder entities
 */
@Dao
interface ReminderDao {

    /**
     * Insert a new reminder into the database
     */
    @Insert
    suspend fun insertReminder(reminder: Reminder): Long

    /**
     * Insert multiple reminders
     */
    @Insert
    suspend fun insertReminders(reminders: List<Reminder>)

    /**
     * Update an existing reminder
     */
    @Update
    suspend fun updateReminder(reminder: Reminder)

    /**
     * Delete a reminder
     */
    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    /**
     * Delete a reminder by ID
     */
    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    /**
     * Get all reminders ordered by creation time (newest first)
     */
    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun getAllReminders(): Flow<List<Reminder>>

    /**
     * Get all reminders ordered by scheduled time
     */
    @Query("SELECT * FROM reminders ORDER BY scheduledTime ASC")
    fun getAllRemindersByScheduledTime(): Flow<List<Reminder>>

    /**
     * Get a reminder by ID
     */
    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: String): Reminder?

    /**
     * Get active reminders (not triggered yet)
     */
    @Query("SELECT * FROM reminders WHERE isActive = 1 AND isTriggered = 0 ORDER BY scheduledTime ASC")
    suspend fun getActiveReminders(): List<Reminder>

    /**
     * Get active reminders as Flow
     */
    @Query("SELECT * FROM reminders WHERE isActive = 1 AND isTriggered = 0 ORDER BY scheduledTime ASC")
    fun getActiveRemindersFlow(): Flow<List<Reminder>>

    /**
     * Get overdue reminders (scheduled time has passed but not triggered)
     */
    @Query("SELECT * FROM reminders WHERE isActive = 1 AND isTriggered = 0 AND scheduledTime < :currentTime")
    suspend fun getOverdueReminders(currentTime: Long = System.currentTimeMillis()): List<Reminder>

    /**
     * Get future reminders (scheduled for future)
     */
    @Query("SELECT * FROM reminders WHERE scheduledTime > :currentTime ORDER BY scheduledTime ASC")
    suspend fun getFutureReminders(currentTime: Long = System.currentTimeMillis()): List<Reminder>

    /**
     * Get reminders by category
     */
    @Query("SELECT * FROM reminders WHERE category = :category ORDER BY scheduledTime ASC")
    fun getRemindersByCategory(category: String): Flow<List<Reminder>>

    /**
     * Get reminders scheduled within a time range
     */
    @Query("SELECT * FROM reminders WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    fun getRemindersInTimeRange(startTime: Long, endTime: Long): Flow<List<Reminder>>

    /**
     * Get reminders for today
     */
    @Query("""
        SELECT * FROM reminders
        WHERE scheduledTime >= :startOfDay
        AND scheduledTime < :endOfDay
        ORDER BY scheduledTime ASC
    """)
    suspend fun getTodayReminders(startOfDay: Long, endOfDay: Long): List<Reminder>

    /**
     * Search reminders by message content
     */
    @Query("SELECT * FROM reminders WHERE message LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchReminders(query: String): Flow<List<Reminder>>

    /**
     * Get next upcoming reminder
     */
    @Query("SELECT * FROM reminders WHERE isActive = 1 AND isTriggered = 0 AND scheduledTime > :currentTime ORDER BY scheduledTime ASC LIMIT 1")
    suspend fun getNextReminder(currentTime: Long = System.currentTimeMillis()): Reminder?

    /**
     * Get reminder count
     */
    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun getReminderCount(): Int

    /**
     * Get active reminder count
     */
    @Query("SELECT COUNT(*) FROM reminders WHERE isActive = 1 AND isTriggered = 0")
    suspend fun getActiveReminderCount(): Int

    /**
     * Get triggered reminder count
     */
    @Query("SELECT COUNT(*) FROM reminders WHERE isTriggered = 1")
    suspend fun getTriggeredReminderCount(): Int

    /**
     * Mark reminder as triggered
     */
    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :reminderId")
    suspend fun markAsTriggered(reminderId: String)

    /**
     * Update reminder active status
     */
    @Query("UPDATE reminders SET isActive = :active WHERE id = :reminderId")
    suspend fun updateActiveStatus(reminderId: String, active: Boolean)

    /**
     * Update reminder scheduled time
     */
    @Query("UPDATE reminders SET scheduledTime = :newTime WHERE id = :reminderId")
    suspend fun updateScheduledTime(reminderId: String, newTime: Long)

    /**
     * Update message association
     */
    @Query("UPDATE reminders SET messageId = :messageId WHERE id = :reminderId")
    suspend fun updateMessageId(reminderId: String, messageId: String?)

    /**
     * Get reminder by alarm ID
     */
    @Query("SELECT * FROM reminders WHERE alarmId = :alarmId")
    suspend fun getReminderByAlarmId(alarmId: Int): Reminder?

    /**
     * Delete all reminders
     */
    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()

    /**
     * Delete all triggered reminders
     */
    @Query("DELETE FROM reminders WHERE isTriggered = 1")
    suspend fun deleteTriggeredReminders()

    /**
     * Delete old reminders (created before specified time)
     */
    @Query("DELETE FROM reminders WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOldReminders(beforeTimestamp: Long)

    /**
     * Deactivate all reminders
     */
    @Query("UPDATE reminders SET isActive = 0")
    suspend fun deactivateAllReminders()

    /**
     * Reactivate all reminders
     */
    @Query("UPDATE reminders SET isActive = 1 WHERE isTriggered = 0")
    suspend fun reactivateAllReminders()
}