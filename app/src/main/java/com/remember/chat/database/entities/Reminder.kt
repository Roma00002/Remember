package com.remember.chat.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a scheduled reminder
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /**
     * The reminder message content
     */
    val message: String,

    /**
     * The scheduled time when the reminder should trigger (in milliseconds)
     */
    val scheduledTime: Long,

    /**
     * Timestamp when the reminder was created (in milliseconds)
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Whether the reminder is currently active and scheduled
     */
    val isActive: Boolean = true,

    /**
     * Whether the reminder has been triggered
     */
    val isTriggered: Boolean = false,

    /**
     * The ID of the associated message in the chat
     */
    val messageId: String? = null,

    /**
     * The alarm ID used by AlarmManager for this reminder
     */
    val alarmId: Int = id.hashCode(),

    /**
     * Optional category or tag for the reminder
     */
    val category: String? = null
) {
    /**
     * Helper function to check if reminder is scheduled for the future
     */
    fun isFuture(): Boolean {
        return scheduledTime > System.currentTimeMillis()
    }

    /**
     * Helper function to check if reminder is overdue
     */
    fun isOverdue(): Boolean {
        return !isTriggered && scheduledTime < System.currentTimeMillis()
    }

    /**
     * Helper function to format scheduled time for display
     */
    fun getFormattedScheduledTime(): String {
        val date = java.util.Date(scheduledTime)
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Helper function to format creation time for display
     */
    fun getFormattedCreationTime(): String {
        val date = java.util.Date(createdAt)
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Helper function to get time until reminder
     */
    fun getTimeUntil(): Long {
        return scheduledTime - System.currentTimeMillis()
    }

    /**
     * Helper function to get a human-readable time until reminder
     */
    fun getTimeUntilDisplay(): String {
        val timeUntil = getTimeUntil()
        if (timeUntil <= 0) return "Vencido"

        val seconds = timeUntil / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}