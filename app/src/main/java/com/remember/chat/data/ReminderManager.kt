package com.remember.chat.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.remember.chat.database.entities.Reminder
import com.remember.chat.notification.ReminderReceiver

/**
 * Manager for scheduling and canceling reminder alarms using AlarmManager
 * Handles exact alarm scheduling with proper permissions and fallbacks
 */
class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "ReminderManager"
        private const val REMINDER_REQUEST_CODE_BASE = 1000
        private const val REMINDER_INTENT_ACTION = "com.remember.chat.ACTION_REMINDER_TRIGGER"

        // Intent extras
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_MESSAGE = "reminder_message"
        const val EXTRA_REMINDER_SCHEDULED_TIME = "reminder_scheduled_time"
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    /**
     * Schedule a reminder alarm
     */
    fun scheduleReminder(reminder: Reminder): Boolean {
        return try {
            if (reminder.scheduledTime <= System.currentTimeMillis()) {
                Log.w(TAG, "Cannot schedule reminder in the past: ${reminder.id}")
                return false
            }

            val alarmId = reminder.alarmId
            val intent = createReminderIntent(reminder)
            val pendingIntent = createPendingIntent(alarmId, intent)

            // Schedule the alarm
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ requires exact alarm permission check
                    if (alarmManager.canScheduleExactAlarms()) {
                        scheduleExactAlarm(alarmId, pendingIntent, reminder.scheduledTime)
                    } else {
                        scheduleInexactAlarm(pendingIntent, reminder.scheduledTime)
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Android 6.0+ use setExactAndAllowWhileIdle
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.scheduledTime,
                        pendingIntent
                    )
                }
                else -> {
                    // Older Android versions
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminder.scheduledTime,
                        pendingIntent
                    )
                }
            }

            Log.d(TAG, "Reminder scheduled successfully: ${reminder.id} at ${reminder.scheduledTime}")
            true

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when scheduling reminder: ${e.message}")
            // Fallback to inexact alarm
            scheduleInexactAlarm(reminder)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling reminder: ${e.message}", e)
            false
        }
    }

    /**
     * Schedule an exact alarm (Android 12+)
     */
    private fun scheduleExactAlarm(alarmId: Int, pendingIntent: PendingIntent, scheduledTime: Long) {
        try {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                scheduledTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot schedule exact alarm, falling back to inexact alarm")
            scheduleInexactAlarm(pendingIntent, scheduledTime)
        }
    }

    /**
     * Schedule an inexact alarm as fallback
     */
    private fun scheduleInexactAlarm(pendingIntent: PendingIntent, scheduledTime: Long) {
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            scheduledTime,
            pendingIntent
        )
    }

    /**
     * Schedule an inexact alarm from reminder object
     */
    private fun scheduleInexactAlarm(reminder: Reminder) {
        try {
            val intent = createReminderIntent(reminder)
            val pendingIntent = createPendingIntent(reminder.alarmId, intent)
            scheduleInexactAlarm(pendingIntent, reminder.scheduledTime)
            Log.d(TAG, "Reminder scheduled with inexact alarm: ${reminder.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling inexact alarm: ${e.message}", e)
        }
    }

    /**
     * Cancel a reminder alarm
     */
    fun cancelReminder(reminderId: String, alarmId: Int): Boolean {
        return try {
            val intent = createCancelIntent(reminderId)
            val pendingIntent = createPendingIntent(alarmId, intent)
            alarmManager.cancel(pendingIntent)

            Log.d(TAG, "Reminder cancelled: $reminderId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling reminder: ${e.message}", e)
            false
        }
    }

    /**
     * Cancel a reminder alarm by alarm ID only
     */
    fun cancelReminder(alarmId: Int): Boolean {
        return try {
            val intent = Intent(REMINDER_INTENT_ACTION)
            val pendingIntent = createPendingIntent(alarmId, intent)
            alarmManager.cancel(pendingIntent)

            Log.d(TAG, "Reminder cancelled by alarm ID: $alarmId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling reminder by alarm ID: ${e.message}", e)
            false
        }
    }

    /**
     * Reschedule a reminder with new time
     */
    fun rescheduleReminder(reminder: Reminder, newScheduledTime: Long): Boolean {
        // Cancel the old alarm first
        cancelReminder(reminder.id, reminder.alarmId)

        // Schedule new alarm with updated time
        val updatedReminder = reminder.copy(scheduledTime = newScheduledTime)
        return scheduleReminder(updatedReminder)
    }

    /**
     * Check if exact alarms can be scheduled
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // For older versions, assume exact alarms are available
        }
    }

    /**
     * Get next scheduled alarm time
     */
    fun getNextAlarmTime(): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmInfo = alarmManager.nextAlarmClock
                alarmInfo?.triggerTime
            } else {
                // For older versions, we can't easily get next alarm time
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting next alarm time: ${e.message}")
            null
        }
    }

    /**
     * Create intent for reminder trigger
     */
    private fun createReminderIntent(reminder: Reminder): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            action = REMINDER_INTENT_ACTION
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_MESSAGE, reminder.message)
            putExtra(EXTRA_REMINDER_SCHEDULED_TIME, reminder.scheduledTime)
            putExtra(EXTRA_ALARM_ID, reminder.alarmId)
        }
    }

    /**
     * Create intent for cancellation
     */
    private fun createCancelIntent(reminderId: String): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            action = REMINDER_INTENT_ACTION
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
    }

    /**
     * Create PendingIntent for alarm
     */
    private fun createPendingIntent(alarmId: Int, intent: Intent): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            flags
        )
    }

    /**
     * Schedule multiple reminders (batch operation)
     */
    fun scheduleMultipleReminders(reminders: List<Reminder>): Int {
        var successCount = 0
        reminders.forEach { reminder ->
            if (scheduleReminder(reminder)) {
                successCount++
            }
        }
        Log.d(TAG, "Scheduled $successCount out of ${reminders.size} reminders")
        return successCount
    }

    /**
     * Cancel all reminder alarms
     */
    fun cancelAllReminders(alarmIds: List<Int>): Int {
        var successCount = 0
        alarmIds.forEach { alarmId ->
            if (cancelReminder(alarmId)) {
                successCount++
            }
        }
        Log.d(TAG, "Cancelled $successCount out of ${alarmIds.size} reminders")
        return successCount
    }

    /**
     * Check if a specific alarm is scheduled
     */
    fun isAlarmScheduled(alarmId: Int): Boolean {
        return try {
            val intent = Intent(REMINDER_INTENT_ACTION)
            val pendingIntent = createPendingIntent(alarmId, intent)
            pendingIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if alarm is scheduled: ${e.message}")
            false
        }
    }

    /**
     * Get alarm information for debugging
     */
    fun getAlarmInfo(reminder: Reminder): String {
        return buildString {
            appendLine("Reminder Alarm Info:")
            appendLine("ID: ${reminder.id}")
            appendLine("Alarm ID: ${reminder.alarmId}")
            appendLine("Message: ${reminder.message}")
            appendLine("Scheduled Time: ${reminder.getFormattedScheduledTime()}")
            appendLine("Time Until: ${reminder.getTimeUntilDisplay()}")
            appendLine("Can Schedule Exact: ${canScheduleExactAlarms()}")
            appendLine("Is Future: ${reminder.isFuture()}")
            appendLine("Is Active: ${reminder.isActive}")
        }
    }

    /**
     * Validate reminder before scheduling
     */
    fun validateReminderForScheduling(reminder: Reminder): ValidationResult {
        return when {
            !reminder.isActive -> ValidationResult(false, "Reminder is not active")
            reminder.isTriggered -> ValidationResult(false, "Reminder has already been triggered")
            !reminder.isFuture() -> ValidationResult(false, "Reminder time is in the past")
            reminder.message.isBlank() -> ValidationResult(false, "Reminder message is empty")
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