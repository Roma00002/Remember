package com.remember.chat.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.remember.chat.data.ReminderManager
import com.remember.chat.notification.NotificationHelper

/**
 * Manages runtime permissions for the Remember chat app
 * Handles notification permissions, exact alarm permissions, and user guidance
 */
class PermissionManager(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "PermissionManager"

        // Permission request codes
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val EXACT_ALARM_PERMISSION_REQUEST_CODE = 1002

        // Permission check results
        const val PERMISSION_GRANTED = PackageManager.PERMISSION_GRANTED
        const val PERMISSION_DENIED = PackageManager.PERMISSION_DENIED
        const val PERMISSION_NEVER_ASK_AGAIN = -1

        // Permission groups
        const val PERMISSION_GROUP_NOTIFICATIONS = "notifications"
        const val PERMISSION_GROUP_ALARMS = "alarms"
        const val PERMISSION_GROUP_ALL = "all"
    }

    private val context = activity as Context

    /**
     * Check if a specific permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PERMISSION_GRANTED
    }

    /**
     * Check all required permissions for the app
     */
    fun checkAllPermissions(): PermissionStatus {
        val notificationStatus = checkNotificationPermission()
        val alarmStatus = checkExactAlarmPermission()

        return PermissionStatus(
            notifications = notificationStatus,
            exactAlarm = alarmStatus,
            allGranted = notificationStatus.isGranted && alarmStatus.isGranted
        )
    }

    /**
     * Check notification permission status
     */
    fun checkNotificationPermission(): PermissionDetail {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )

            PermissionDetail(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                isGranted = isGranted,
                shouldShowRationale = shouldShowRationale,
                minSdkVersion = Build.VERSION_CODES.TIRAMISU,
                description = if (isGranted) {
                    "Notificaciones habilitadas"
                } else if (shouldShowRationale) {
                    "Las notificaciones son necesarias para los recordatorios"
                } else {
                    "Notificaciones deshabilitadas - Se requiere permiso"
                }
            )
        } else {
            // For Android versions below 13, notifications are enabled by default
            PermissionDetail(
                permission = "legacy_notifications",
                isGranted = true,
                shouldShowRationale = false,
                minSdkVersion = 0,
                description = "Notificaciones habilitadas (versión heredada)"
            )
        }
    }

    /**
     * Check exact alarm permission status
     */
    fun checkExactAlarmPermission(): PermissionDetail {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val reminderManager = ReminderManager(context)
            val canScheduleExact = reminderManager.canScheduleExactAlarms()

            PermissionDetail(
                permission = "android.permission.SCHEDULE_EXACT_ALARM",
                isGranted = canScheduleExact,
                shouldShowRationale = !canScheduleExact,
                minSdkVersion = Build.VERSION_CODES.S,
                description = if (canScheduleExact) {
                    "Alarmas exactas habilitadas"
                } else {
                    "Se requiere permiso de alarmas exactas para recordatorios precisos"
                }
            )
        } else {
            // For Android versions below 12, exact alarms don't require special permission
            PermissionDetail(
                permission = "legacy_exact_alarms",
                isGranted = true,
                shouldShowRationale = false,
                minSdkVersion = 0,
                description = "Alarmas exactas habilitadas (versión heredada)"
            )
        }
    }

    /**
     * Request notification permission
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Request exact alarm permission (opens settings)
     */
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app settings
                openAppSettings()
            }
        }
    }

    /**
     * Request all necessary permissions
     */
    fun requestAllPermissions() {
        val notificationStatus = checkNotificationPermission()
        val alarmStatus = checkExactAlarmPermission()

        when {
            !notificationStatus.isGranted -> {
                requestNotificationPermission()
            }
            !alarmStatus.isGranted -> {
                requestExactAlarmPermission()
            }
            else -> {
                // All permissions already granted
                android.util.Log.d(TAG, "All permissions already granted")
            }
        }
    }

    /**
     * Handle permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): PermissionResult {
        return when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (permissions.isNotEmpty() && grantResults.isNotEmpty()) {
                    val isGranted = grantResults[0] == PERMISSION_GRANTED
                    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )

                    PermissionResult(
                        permissionGroup = PERMISSION_GROUP_NOTIFICATIONS,
                        isGranted = isGranted,
                        shouldShowRationale = shouldShowRationale,
                        requestCode = requestCode
                    )
                } else {
                    PermissionResult(
                        permissionGroup = PERMISSION_GROUP_NOTIFICATIONS,
                        isGranted = false,
                        shouldShowRationale = false,
                        requestCode = requestCode
                    )
                }
            }
            EXACT_ALARM_PERMISSION_REQUEST_CODE -> {
                // Exact alarm permission is handled via settings, so we just check current status
                val alarmStatus = checkExactAlarmPermission()
                PermissionResult(
                    permissionGroup = PERMISSION_GROUP_ALARMS,
                    isGranted = alarmStatus.isGranted,
                    shouldShowRationale = !alarmStatus.isGranted,
                    requestCode = requestCode
                )
            }
            else -> {
                PermissionResult(
                    permissionGroup = "unknown",
                    isGranted = false,
                    shouldShowRationale = false,
                    requestCode = requestCode
                )
            }
        }
    }

    /**
     * Open app settings for manual permission management
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening app settings: ${e.message}")
        }
    }

    /**
     * Get user-friendly permission status message
     */
    fun getPermissionStatusMessage(): String {
        val status = checkAllPermissions()
        return buildString {
            if (status.allGranted) {
                appendLine("✅ Todos los permisos necesarios están habilitados")
            } else {
                appendLine("⚠️ Se necesitan los siguientes permisos:")
                appendLine()

                if (!status.notifications.isGranted) {
                    appendLine("📢 Notificaciones: ${status.notifications.description}")
                }
                if (!status.exactAlarm.isGranted) {
                    appendLine("⏰ Alarmas exactas: ${status.exactAlarm.description}")
                }
                appendLine()
                appendLine("Haz clic en 'Solicitar Permisos' para habilitarlos")
            }
        }
    }

    /**
     * Check if the app can function properly with current permissions
     */
    fun canFunctionProperly(): Boolean {
        val status = checkAllPermissions()

        // Basic functionality (chat) works without permissions
        // But reminders require both notification and exact alarm permissions
        return true // App can function, but with limited features
    }

    /**
     * Check if reminders can work with current permissions
     */
    fun canRemindersWork(): Boolean {
        val status = checkAllPermissions()
        return status.notifications.isGranted && status.exactAlarm.isGranted
    }

    /**
     * Get permission explanation for user
     */
    fun getPermissionExplanation(permissionGroup: String): String {
        return when (permissionGroup) {
            PERMISSION_GROUP_NOTIFICATIONS -> {
                """
                    Las notificaciones son esenciales para:

                    • Mostrar recordatorios programados
                    • Alertar cuando un recordatorio vence
                    • Recordar eventos importantes

                    Sin notificaciones, no podrás recibir alertas de recordatorios.
                """.trimIndent()
            }
            PERMISSION_GROUP_ALARMS -> {
                """
                    Los permisos de alarma son necesarios para:

                    • Programar recordatorios exactos
                    • Activar recordatorios en tiempo específico
                    • Garantizar que los recordatorios funcionen correctamente

                    Este permiso asegura que los recordatorios se activen en el momento exacto.
                """.trimIndent()
            }
            else -> "Este permiso es necesario para el correcto funcionamiento de la aplicación."
        }
    }

    /**
     * Create activity result launcher for notification permission
     */
    fun createNotificationPermissionLauncher(): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                android.util.Log.d(TAG, "Notification permission granted")
            } else {
                android.util.Log.d(TAG, "Notification permission denied")
            }
        }
    }

    /**
     * Create activity result launcher for multiple permissions
     */
    fun createMultiplePermissionLauncher(): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.forEach { (permission, isGranted) ->
                android.util.Log.d(TAG, "Permission $permission: ${if (isGranted) "granted" else "denied"}")
            }
        }
    }
}

/**
 * Data class for overall permission status
 */
data class PermissionStatus(
    val notifications: PermissionDetail,
    val exactAlarm: PermissionDetail,
    val allGranted: Boolean
)

/**
 * Data class for individual permission details
 */
data class PermissionDetail(
    val permission: String,
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val minSdkVersion: Int,
    val description: String
)

/**
 * Data class for permission request results
 */
data class PermissionResult(
    val permissionGroup: String,
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val requestCode: Int
)