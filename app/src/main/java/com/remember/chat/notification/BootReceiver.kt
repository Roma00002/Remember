package com.remember.chat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that handles device boot events
 * Ensures reminders are rescheduled after device restart
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver triggered with action: ${intent.action}")

        // Forward the boot event to ReminderReceiver for processing
        val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = intent.action
        }

        // Send the broadcast to ReminderReceiver
        context.sendBroadcast(reminderIntent)
    }
}