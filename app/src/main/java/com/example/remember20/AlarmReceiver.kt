package com.example.remember20

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("Remember20", "AlarmReceiver: onReceive triggered")
        val message = intent.getStringExtra("MESSAGE") ?: "Recordatorio"
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a new channel ID to ensure settings are updated
        val channelId = "reminder_channel_high_priority"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Recordatorios Urgentes",
                NotificationManager.IMPORTANCE_HIGH // IMPORTANCE_MAX requires user permission on some versions, HIGH is safer default but let's try HIGH first with fullScreenIntent
            ).apply {
                description = "Canal para alarmas de recordatorio"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            // Try to set importance to MAX if possible, but HIGH is usually enough for fullScreenIntent
            channel.importance = NotificationManager.IMPORTANCE_HIGH
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("MESSAGE", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // Unique request code
            fullScreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Recordatorio")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX for pre-O
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true) // Make it harder to dismiss accidentally
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        // On Android 10+ (API 29+), starting activities from the background is restricted.
        // The setFullScreenIntent in the notification is the correct way to handle this.
        // We only attempt direct start for older versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                context.startActivity(fullScreenIntent)
            } catch (e: Exception) {
                android.util.Log.e("Remember20", "Could not start activity directly: ${e.message}")
            }
        }
    }
}
