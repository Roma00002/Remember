package com.remember.chat.repository

import com.remember.chat.database.AppDatabase
import com.remember.chat.database.entities.Message
import com.remember.chat.database.entities.MessageType
import com.remember.chat.database.entities.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repository that provides a clean API for data access
 * Serves as a mediator between data sources (Room database) and the rest of the app
 */
class ChatRepository(private val database: AppDatabase) {

    // DAO instances
    private val messageDao = database.messageDao()
    private val reminderDao = database.reminderDao()

    // ==================== Message Operations ====================

    /**
     * Get all messages as a Flow
     */
    fun getAllMessages(): Flow<List<Message>> {
        return messageDao.getAllMessages()
    }

    /**
     * Send a new message
     */
    suspend fun sendMessage(content: String): Message {
        val message = Message(
            content = content,
            type = MessageType.SENT,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        return message
    }

    /**
     * Add a received message
     */
    suspend fun addReceivedMessage(content: String): Message {
        val message = Message(
            content = content,
            type = MessageType.RECEIVED,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        return message
    }

    /**
     * Create a reminder message
     */
    suspend fun createReminderMessage(content: String, scheduledTime: Long, reminderId: String): Message {
        val message = Message(
            content = content,
            type = MessageType.REMINDER,
            isReminder = true,
            reminderTime = scheduledTime,
            reminderId = reminderId,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        return message
    }

    /**
     * Mark a reminder message as triggered
     */
    suspend fun markReminderMessageTriggered(messageId: String) {
        messageDao.updateReminderTriggeredStatus(messageId, true)
    }

    /**
     * Mark a reminder as triggered by reminder ID
     */
    suspend fun markReminderAsTriggered(reminderId: String) {
        messageDao.markReminderAsTriggered(reminderId)
        reminderDao.markAsTriggered(reminderId)
    }

    /**
     * Get reminder messages
     */
    fun getReminderMessages(): Flow<List<Message>> {
        return messageDao.getReminderMessages()
    }

    /**
     * Get active reminder messages
     */
    suspend fun getActiveReminderMessages(): List<Message> {
        return messageDao.getActiveReminderMessages()
    }

    /**
     * Search messages
     */
    fun searchMessages(query: String): Flow<List<Message>> {
        return messageDao.searchMessages(query)
    }

    /**
     * Delete a message
     */
    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    /**
     * Get message count by type
     */
    suspend fun getMessageCount(type: MessageType): Int {
        return messageDao.getMessageCountByType(type)
    }

    // ==================== Reminder Operations ====================

    /**
     * Schedule a new reminder
     */
    suspend fun scheduleReminder(
        message: String,
        scheduledTime: Long,
        category: String? = null
    ): Reminder {
        val reminder = Reminder(
            message = message,
            scheduledTime = scheduledTime,
            category = category
        )
        reminderDao.insertReminder(reminder)
        return reminder
    }

    /**
     * Schedule a reminder and create associated message
     */
    suspend fun scheduleReminderWithMessage(
        message: String,
        scheduledTime: Long,
        category: String? = null
    ): Pair<Reminder, Message> {
        // Create reminder first
        val reminder = scheduleReminder(message, scheduledTime, category)

        // Create associated message
        val reminderMessage = createReminderMessage(message, scheduledTime, reminder.id)

        // Update reminder with message ID
        reminderDao.updateMessageId(reminder.id, reminderMessage.id)

        return Pair(reminder, reminderMessage)
    }

    /**
     * Get all reminders
     */
    fun getAllReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllReminders()
    }

    /**
     * Get active reminders
     */
    fun getActiveReminders(): Flow<List<Reminder>> {
        return reminderDao.getActiveRemindersFlow()
    }

    /**
     * Get overdue reminders
     */
    suspend fun getOverdueReminders(): List<Reminder> {
        return reminderDao.getOverdueReminders()
    }

    /**
     * Get next upcoming reminder
     */
    suspend fun getNextReminder(): Reminder? {
        return reminderDao.getNextReminder()
    }

    /**
     * Update reminder status
     */
    suspend fun updateReminderActiveStatus(reminderId: String, isActive: Boolean) {
        reminderDao.updateActiveStatus(reminderId, isActive)
    }

    /**
     * Reschedule a reminder
     */
    suspend fun rescheduleReminder(reminderId: String, newScheduledTime: Long) {
        reminderDao.updateScheduledTime(reminderId, newScheduledTime)
    }

    /**
     * Delete a reminder
     */
    suspend fun deleteReminder(reminderId: String) {
        reminderDao.deleteReminderById(reminderId)

        // Also delete associated message if it exists
        val associatedMessages = messageDao.getActiveReminderMessages()
            .filter { it.reminderId == reminderId }

        associatedMessages.forEach { message ->
            messageDao.deleteMessageById(message.id)
        }
    }

    /**
     * Get reminder by ID
     */
    suspend fun getReminderById(reminderId: String): Reminder? {
        return reminderDao.getReminderById(reminderId)
    }

    /**
     * Get reminder by alarm ID
     */
    suspend fun getReminderByAlarmId(alarmId: Int): Reminder? {
        return reminderDao.getReminderByAlarmId(alarmId)
    }

    // ==================== Utility Operations ====================

    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): AppDatabase.DatabaseStats {
        return database.getDatabaseStats()
    }

    /**
     * Clear all data
     */
    suspend fun clearAllData() {
        database.clearAllData()
    }

    /**
     * Get chat statistics formatted for display
     */
    fun getChatStats(): Flow<ChatStats> {
        return getAllMessages().map { messages ->
            val sentCount = messages.count { it.type == MessageType.SENT }
            val receivedCount = messages.count { it.type == MessageType.RECEIVED }
            val reminderCount = messages.count { it.isReminder }
            val todayMessages = messages.count {
                val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))
                it.timestamp >= todayStart
            }

            ChatStats(
                totalMessages = messages.size,
                sentMessages = sentCount,
                receivedMessages = receivedCount,
                reminderMessages = reminderCount,
                todayMessages = todayMessages
            )
        }
    }

    /**
     * Data class for chat statistics
     */
    data class ChatStats(
        val totalMessages: Int,
        val sentMessages: Int,
        val receivedMessages: Int,
        val reminderMessages: Int,
        val todayMessages: Int
    )

    // ==================== Data Validation ====================

    /**
     * Validate message content
     */
    fun validateMessage(content: String): ValidationResult {
        return when {
            content.isBlank() -> ValidationResult(false, "El mensaje no puede estar vacío")
            content.length > 1000 -> ValidationResult(false, "El mensaje es demasiado largo")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Validate reminder scheduling
     */
    fun validateReminderTime(scheduledTime: Long): ValidationResult {
        val currentTime = System.currentTimeMillis()
        return when {
            scheduledTime <= currentTime -> ValidationResult(false, "El recordatorio debe programarse para el futuro")
            scheduledTime > currentTime + (365L * 24 * 60 * 60 * 1000) ->
                ValidationResult(false, "El recordatorio no puede programarse para más de un año en el futuro")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Validation result data class
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}