package com.remember.chat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.remember.chat.MainActivity

/**
 * Helper class for managing notifications in the Remember chat app
 * Handles notification channel creation, permission checking, and notification building
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        private const val TAG = "NotificationHelper"

        // Notification channel
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val REMINDER_CHANNEL_NAME = "Recordatorios"
        const val REMINDER_CHANNEL_DESCRIPTION = "Notificaciones para recordatorios programados"

        // Notification IDs
        private const val REMINDER_NOTIFICATION_BASE = 2000
        private const val SERVICE_NOTIFICATION_ID = 3000

        // Request codes
        private const val REMINDER_PENDING_INTENT_REQUEST = 1001

        // Notification actions
        const val ACTION_DISMISS = "action_dismiss"
        const val ACTION_SNOOZE = "action_snooze"
        const val ACTION_OPEN_CHAT = "action_open_chat"

        // Snooze times (in milliseconds)
        const val SNOOZE_5_MINUTES = 5 * 60 * 1000L
        const val SNOOZE_15_MINUTES = 15 * 60 * 1000L
        const val SNOOZE_1_HOUR = 60 * 60 * 1000L
    }

    /**
     * Initialize notification channels (call this on app startup)
     */
    fun initializeNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createReminderChannel()
        }
    }

    /**
     * Check if notification permissions are granted
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager.areNotificationsEnabled()
        }
    }

    /**
     * Show a reminder notification
     */
    fun showReminderNotification(
        reminderId: String,
        message: String,
        scheduledTime: Long,
        notificationId: Int = REMINDER_NOTIFICATION_BASE + reminderId.hashCode().rem(1000)
    ): Boolean {
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are not enabled, cannot show reminder notification")
            return false
        }

        try {
            val notification = buildReminderNotification(reminderId, message, scheduledTime)
            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "Reminder notification shown for: $reminderId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing reminder notification: ${e.message}", e)
            return false
        }
    }

    /**
     * Build a reminder notification
     */
    private fun buildReminderNotification(
        reminderId: String,
        message: String,
        scheduledTime: Long
    ): androidx.core.app.NotificationCompat.Builder {
        // Create intent for opening the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminderId)
            putExtra("open_reminders", true)
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REMINDER_PENDING_INTENT_REQUEST,
            openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Create dismiss action
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("reminder_id", reminderId)
            putExtra("notification_id", REMINDER_NOTIFICATION_BASE + reminderId.hashCode().rem(1000))
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            dismissIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Create snooze action
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("reminder_id", reminderId)
            putExtra("snooze_time", SNOOZE_15_MINUTES)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode() + 1,
            snoozeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with app icon later
            .setContentTitle("⏰ Recordatorio")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Descartar",
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "Posponer 15 min",
                snoozePendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(scheduledTime)
            .setShowWhen(true)
    }

    /**
     * Show a simple notification (for general use)
     */
    fun showSimpleNotification(
        title: String,
        message: String,
        notificationId: Int
    ): Boolean {
        if (!areNotificationsEnabled()) {
            return false
        }

        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing simple notification: ${e.message}", e)
            return false
        }
    }

    /**
     * Cancel a notification
     */
    fun cancelNotification(notificationId: Int) {
        try {
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Notification cancelled: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification: ${e.message}", e)
        }
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.d(TAG, "All notifications cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling all notifications: ${e.message}", e)
        }
    }

    /**
     * Create the reminder notification channel
     */
    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = REMINDER_CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            Log.d(TAG, "Reminder notification channel created")
        }
    }

    /**
     * Get notification channel information
     */
    fun getNotificationChannelInfo(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = manager.getNotificationChannel(REMINDER_CHANNEL_ID)
                if (channel != null) {
                    buildString {
                        appendLine("Notification Channel Info:")
                        appendLine("ID: ${channel.id}")
                        appendLine("Name: ${channel.name}")
                        appendLine("Description: ${channel.description}")
                        appendLine("Importance: ${channel.importance}")
                        appendLine("Sound: ${channel.sound}")
                        appendLine("Vibration: ${channel.vibrationPattern != null}")
                        appendLine("Lights: ${channel.enableLights()}")
                        appendLine("Show Badge: ${channel.canShowBadge()}")
                    }
                } else {
                    "Notification channel not found"
                }
            } catch (e: Exception) {
                "Error getting channel info: ${e.message}"
            }
        } else {
            "Notification channels not supported on this Android version"
        }
    }

    /**
     * Show a notification for testing purposes
     */
    fun showTestNotification(): Boolean {
        return showSimpleNotification(
            "Test Notification",
            "This is a test notification from Remember app",
            9999
        )
    }

    /**
     * Check if the app can show notifications
     */
    fun canShowNotifications(): Boolean {
        return areNotificationsEnabled()
    }

    /**
     * Get notification permission status description
     */
    fun getNotificationPermissionStatus(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (areNotificationsEnabled()) {
                    "Notificaciones habilitadas (Android 13+)"
                } else {
                    "Notificaciones no habilitadas - Se requiere permiso POST_NOTIFICATIONS"
                }
            }
            else -> {
                if (notificationManager.areNotificationsEnabled()) {
                    "Notificaciones habilitadas"
                } else {
                    "Notificaciones deshabilitadas en configuración del sistema"
                }
            }
        }
    }
}