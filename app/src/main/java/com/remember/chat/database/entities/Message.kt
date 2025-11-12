package com.remember.chat.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a message in the chat
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /**
     * The content of the message
     */
    val content: String,

    /**
     * Timestamp when the message was created (in milliseconds)
     */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Type of the message (SENT, RECEIVED, REMINDER)
     */
    val type: MessageType,

    /**
     * Whether this message is a reminder
     */
    val isReminder: Boolean = false,

    /**
     * The scheduled time for the reminder (null for non-reminder messages)
     */
    val reminderTime: Long? = null,

    /**
     * Whether the reminder has been triggered
     */
    val reminderTriggered: Boolean = false,

    /**
     * ID of the associated reminder (if this message represents a reminder)
     */
    val reminderId: String? = null
) {
    /**
     * Helper function to format timestamp for display
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Helper function to format reminder time for display
     */
    fun getFormattedReminderTime(): String? {
        return reminderTime?.let { time ->
            val date = java.util.Date(time)
            val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            format.format(date)
        }
    }

    /**
     * Helper function to check if reminder is scheduled for the future
     */
    fun isFutureReminder(): Boolean {
        return isReminder && reminderTime != null && reminderTime!! > System.currentTimeMillis()
    }
}