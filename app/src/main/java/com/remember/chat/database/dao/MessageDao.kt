package com.remember.chat.database.dao

import androidx.room.*
import com.remember.chat.database.entities.Message
import com.remember.chat.database.entities.MessageType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Message entities
 */
@Dao
interface MessageDao {

    /**
     * Insert a new message into the database
     */
    @Insert
    suspend fun insertMessage(message: Message)

    /**
     * Insert multiple messages
     */
    @Insert
    suspend fun insertMessages(messages: List<Message>)

    /**
     * Update an existing message
     */
    @Update
    suspend fun updateMessage(message: Message)

    /**
     * Delete a message by ID
     */
    @Delete
    suspend fun deleteMessage(message: Message)

    /**
     * Delete a message by ID
     */
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /**
     * Get all messages ordered by timestamp (oldest first)
     */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    /**
     * Get all messages ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesDesc(): Flow<List<Message>>

    /**
     * Get a message by ID
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    /**
     * Get messages by type
     */
    @Query("SELECT * FROM messages WHERE type = :messageType ORDER BY timestamp ASC")
    fun getMessagesByType(messageType: MessageType): Flow<List<Message>>

    /**
     * Get all reminder messages
     */
    @Query("SELECT * FROM messages WHERE isReminder = 1 ORDER BY timestamp ASC")
    fun getReminderMessages(): Flow<List<Message>>

    /**
     * Get active reminder messages (not triggered yet)
     */
    @Query("SELECT * FROM messages WHERE isReminder = 1 AND reminderTriggered = 0 ORDER BY reminderTime ASC")
    suspend fun getActiveReminderMessages(): List<Message>

    /**
     * Get overdue reminder messages
     */
    @Query("SELECT * FROM messages WHERE isReminder = 1 AND reminderTriggered = 0 AND reminderTime < :currentTime")
    suspend fun getOverdueReminderMessages(currentTime: Long = System.currentTimeMillis()): List<Message>

    /**
     * Get messages within a time range
     */
    @Query("SELECT * FROM messages WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getMessagesInTimeRange(startTime: Long, endTime: Long): Flow<List<Message>>

    /**
     * Search messages by content
     */
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<Message>>

    /**
     * Get the latest message
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(): Message?

    /**
     * Get message count by type
     */
    @Query("SELECT COUNT(*) FROM messages WHERE type = :messageType")
    suspend fun getMessageCountByType(messageType: MessageType): Int

    /**
     * Get total message count
     */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int

    /**
     * Update message reminder triggered status
     */
    @Query("UPDATE messages SET reminderTriggered = :triggered WHERE id = :messageId")
    suspend fun updateReminderTriggeredStatus(messageId: String, triggered: Boolean)

    /**
     * Delete all messages
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /**
     * Delete old messages before a certain timestamp
     */
    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)

    /**
     * Mark reminder as triggered by reminder ID
     */
    @Query("UPDATE messages SET reminderTriggered = 1 WHERE reminderId = :reminderId")
    suspend fun markReminderAsTriggered(reminderId: String)

    /**
     * Get messages paginated
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaginated(limit: Int, offset: Int): List<Message>
}