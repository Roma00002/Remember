package com.remember.chat.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.remember.chat.ChatApplication
import com.remember.chat.data.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles reminder alarm triggers
 * Manages notification display, snooze functionality, and reminder state updates
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"

        // Reminder data from ReminderManager
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_MESSAGE = "reminder_message"
        const val EXTRA_REMINDER_SCHEDULED_TIME = "reminder_scheduled_time"
        const val EXTRA_ALARM_ID = "alarm_id"

        // Notification actions from NotificationHelper
        const val ACTION_DISMISS = "action_dismiss"
        const val ACTION_SNOOZE = "action_snooze"
        const val ACTION_OPEN_CHAT = "action_open_chat"

        // Additional extras for actions
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SNOOZE_TIME = "snooze_time"
    }

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var reminderManager: ReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "ReminderReceiver triggered with action: ${intent.action}")

        // Initialize helpers
        notificationHelper = NotificationHelper(context)
        reminderManager = ReminderManager(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                handleBootCompleted(context)
            }
            ReminderManager.REMINDER_INTENT_ACTION -> {
                handleReminderTrigger(context, intent)
            }
            ACTION_DISMISS -> {
                handleDismissAction(context, intent)
            }
            ACTION_SNOOZE -> {
                handleSnoozeAction(context, intent)
            }
            ACTION_OPEN_CHAT -> {
                handleOpenChatAction(context, intent)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    /**
     * Handle reminder alarm trigger
     */
    private fun handleReminderTrigger(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val message = intent.getStringExtra(EXTRA_REMINDER_MESSAGE)
        val scheduledTime = intent.getLongExtra(EXTRA_REMINDER_SCHEDULED_TIME, 0L)
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)

        Log.d(TAG, "Processing reminder trigger - ID: $reminderId, Message: $message")

        if (reminderId == null || message == null) {
            Log.e(TAG, "Invalid reminder data received")
            return
        }

        // Update reminder state in database
        updateReminderState(context, reminderId)

        // Show notification
        showNotificationForReminder(reminderId, message, scheduledTime)

        // Log the trigger for analytics/debugging
        logReminderTrigger(reminderId, message, scheduledTime)
    }

    /**
     * Handle dismiss action from notification
     */
    private fun handleDismissAction(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        Log.d(TAG, "Dismiss action for reminder: $reminderId")

        // Cancel the notification
        if (notificationId != 0) {
            notificationHelper.cancelNotification(notificationId)
        }

        // Update reminder state if needed
        reminderId?.let { updateReminderState(context, it) }
    }

    /**
     * Handle snooze action from notification
     */
    private fun handleSnoozeAction(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val snoozeTime = intent.getLongExtra(EXTRA_SNOOZE_TIME, NotificationHelper.SNOOZE_15_MINUTES)

        Log.d(TAG, "Snooze action for reminder: $reminderId, snooze time: $snoozeTime ms")

        if (reminderId == null) {
            Log.e(TAG, "Cannot snooze: reminder ID is null")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get reminder from database
                val reminder = getReminderFromDatabase(context, reminderId)
                if (reminder != null) {
                    // Cancel current alarm
                    reminderManager.cancelReminder(reminderId, reminder.alarmId)

                    // Calculate new snooze time
                    val newScheduledTime = System.currentTimeMillis() + snoozeTime

                    // Schedule new alarm
                    val snoozedReminder = reminder.copy(
                        scheduledTime = newScheduledTime,
                        isTriggered = false
                    )

                    if (reminderManager.scheduleReminder(snoozedReminder)) {
                        // Update database
                        updateReminderScheduledTime(context, reminderId, newScheduledTime)

                        // Update notification
                        notificationHelper.showSimpleNotification(
                            "Recordatorio Pospuesto",
                            "El recordatorio '${reminder.message}' se ha pospuesto para ${formatSnoozeTime(snoozeTime)}",
                            reminder.alarmId + 1
                        )

                        Log.d(TAG, "Reminder snoozed successfully: $reminderId")
                    } else {
                        Log.e(TAG, "Failed to schedule snoozed reminder: $reminderId")
                    }
                } else {
                    Log.e(TAG, "Reminder not found in database: $reminderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error snoozing reminder: ${e.message}", e)
            }
        }
    }

    /**
     * Handle open chat action from notification
     */
    private fun handleOpenChatAction(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)

        Log.d(TAG, "Open chat action for reminder: $reminderId")

        // Launch MainActivity with reminder data
        val mainIntent = Intent(context, com.remember.chat.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminderId)
            putExtra("open_reminders", true)
        }

        context.startActivity(mainIntent)
    }

    /**
     * Handle device boot completion - reschedule active reminders
     */
    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Boot completed - rescheduling active reminders")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get all active reminders from database
                val activeReminders = getActiveRemindersFromDatabase(context)

                Log.d(TAG, "Found ${activeReminders.size} active reminders to reschedule")

                var successCount = 0
                activeReminders.forEach { reminder ->
                    if (reminderManager.scheduleReminder(reminder)) {
                        successCount++
                        Log.d(TAG, "Rescheduled reminder: ${reminder.id}")
                    } else {
                        Log.e(TAG, "Failed to reschedule reminder: ${reminder.id}")
                    }
                }

                Log.d(TAG, "Rescheduled $successCount out of ${activeReminders.size} reminders")

                // Show status notification if there are rescheduled reminders
                if (successCount > 0) {
                    notificationHelper.showSimpleNotification(
                        "Recordatorios Restablecidos",
                        "Se han reprogramado $successCount recordatorios",
                        8999
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling reminders after boot: ${e.message}", e)
            }
        }
    }

    /**
     * Update reminder state in database to mark as triggered
     */
    private fun updateReminderState(context: Context, reminderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = (context as ChatApplication).database
                database.reminderDao().markAsTriggered(reminderId)
                database.messageDao().markReminderAsTriggered(reminderId)
                Log.d(TAG, "Reminder state updated in database: $reminderId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating reminder state: ${e.message}", e)
            }
        }
    }

    /**
     * Update reminder scheduled time in database
     */
    private suspend fun updateReminderScheduledTime(context: Context, reminderId: String, newTime: Long) {
        try {
            val database = (context as ChatApplication).database
            database.reminderDao().updateScheduledTime(reminderId, newTime)
            Log.d(TAG, "Reminder scheduled time updated: $reminderId -> $newTime")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reminder scheduled time: ${e.message}", e)
        }
    }

    /**
     * Show notification for triggered reminder
     */
    private fun showNotificationForReminder(reminderId: String, message: String, scheduledTime: Long) {
        val notificationId = 2000 + reminderId.hashCode().rem(1000)
        notificationHelper.showReminderNotification(reminderId, message, scheduledTime, notificationId)
    }

    /**
     * Get reminder from database
     */
    private suspend fun getReminderFromDatabase(context: Context, reminderId: String): com.remember.chat.database.entities.Reminder? {
        return try {
            val database = (context as ChatApplication).database
            database.reminderDao().getReminderById(reminderId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting reminder from database: ${e.message}", e)
            null
        }
    }

    /**
     * Get all active reminders from database
     */
    private suspend fun getActiveRemindersFromDatabase(context: Context): List<com.remember.chat.database.entities.Reminder> {
        return try {
            val database = (context as ChatApplication).database
            database.reminderDao().getActiveReminders()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active reminders from database: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Log reminder trigger for analytics
     */
    private fun logReminderTrigger(reminderId: String, message: String, scheduledTime: Long) {
        val currentTime = System.currentTimeMillis()
        val delay = currentTime - scheduledTime

        Log.d(TAG, buildString {
            appendLine("Reminder Trigger Details:")
            appendLine("ID: $reminderId")
            appendLine("Message: $message")
            appendLine("Scheduled: $scheduledTime")
            appendLine("Triggered: $currentTime")
            appendLine("Delay: ${delay}ms")
        })
    }

    /**
     * Format snooze time for display
     */
    private fun formatSnoozeTime(snoozeTimeMs: Long): String {
        val minutes = snoozeTimeMs / (1000 * 60)
        return when {
            minutes < 60 -> "${minutes} minuto${if (minutes != 1L) "s" else ""}"
            minutes < 1440 -> "${minutes / 60} hora${if (minutes / 60 != 1L) "s" else ""}"
            else -> "${minutes / 1440} día${if (minutes / 1440 != 1L) "s" else ""}"
        }
    }
}