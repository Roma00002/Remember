package com.remember.chat

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.remember.chat.permission.PermissionManager
import com.remember.chat.ui.ChatFragment
import com.remember.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Main activity for the Remember chat application
 * Implements single activity architecture with Fragment-based navigation
 * Handles permission requests, initialization, and system events
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ViewModel for chat functionality
    private val chatViewModel: ChatViewModel by viewModels {
        // Custom ViewModelFactory would go here if needed
        defaultViewModelProviderFactory
    }

    // Permission manager
    private lateinit var permissionManager: PermissionManager

    // Permission result launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content view (will be created soon)
        setContentView(R.layout.activity_main)

        // Initialize components
        initializeComponents()

        // Handle intent data (from notifications, etc.)
        handleIntent(intent)

        // Setup UI
        setupUI()

        // Initialize permissions
        initializePermissions()

        Log.d(TAG, "MainActivity created successfully")
    }

    /**
     * Initialize core components
     */
    private fun initializeComponents() {
        // Initialize permission manager
        permissionManager = PermissionManager(this)

        // Initialize notification channels
        initializeNotificationChannels()

        Log.d(TAG, "Core components initialized")
    }

    /**
     * Initialize notification channels
     */
    private fun initializeNotificationChannels() {
        lifecycleScope.launch {
            try {
                com.remember.chat.notification.NotificationHelper(this@MainActivity).initializeNotificationChannels()
                Log.d(TAG, "Notification channels initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing notification channels: ${e.message}", e)
            }
        }
    }

    /**
     * Setup the main UI
     */
    private fun setupUI() {
        // Load the main chat fragment if not already loaded
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatFragment())
                .commitNow()
            Log.d(TAG, "ChatFragment loaded")
        }

        // Setup toolbar if needed
        setupToolbar()
    }

    /**
     * Setup toolbar
     */
    private fun setupToolbar() {
        // Toolbar setup will go here when layout is created
        // For now, just log that it would be set up
        Log.d(TAG, "Toolbar would be set up here")
    }

    /**
     * Initialize and request permissions
     */
    private fun initializePermissions() {
        lifecycleScope.launch {
            try {
                val permissionStatus = permissionManager.checkAllPermissions()

                if (permissionStatus.allGranted) {
                    Log.d(TAG, "All permissions already granted")
                } else {
                    Log.d(TAG, "Some permissions missing: ${permissionManager.getPermissionStatusMessage()}")

                    // Request missing permissions after a short delay
                    if (permissionManager.checkNotificationPermission().isGranted) {
                        // Notifications already granted, check if we need exact alarm permission
                        if (!permissionManager.checkExactAlarmPermission().isGranted) {
                            requestExactAlarmPermission()
                        }
                    } else {
                        // Request notification permission
                        requestNotificationPermission()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing permissions: ${e.message}", e)
            }
        }
    }

    /**
     * Handle notification permission result
     */
    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        Log.d(TAG, "Notification permission result: $isGranted")

        if (isGranted) {
            // Notification permission granted, now check exact alarm permission
            if (!permissionManager.checkExactAlarmPermission().isGranted) {
                requestExactAlarmPermission()
            }
        } else {
            // Permission denied, show rationale or fallback
            showPermissionRationaleIfNeeded()
        }
    }

    /**
     * Request notification permission
     */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Request exact alarm permission
     */
    private fun requestExactAlarmPermission() {
        try {
            permissionManager.requestExactAlarmPermission()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting exact alarm permission: ${e.message}")
        }
    }

    /**
     * Show permission rationale if needed
     */
    private fun showPermissionRationaleIfNeeded() {
        lifecycleScope.launch {
            try {
                val status = permissionManager.checkAllPermissions()
                if (!status.allGranted) {
                    // Show a message to the user about missing permissions
                    // This will be implemented when UI components are ready
                    Log.w(TAG, "Permissions missing: ${permissionManager.getPermissionStatusMessage()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking permission rationale: ${e.message}")
            }
        }
    }

    /**
     * Handle incoming intent data
     */
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            when {
                it.hasExtra("reminder_id") -> {
                    val reminderId = it.getStringExtra("reminder_id")
                    val openReminders = it.getBooleanExtra("open_reminders", false)

                    Log.d(TAG, "Handling reminder intent - ID: $reminderId, Open reminders: $openReminders")

                    // Handle reminder-specific actions
                    handleReminderIntent(reminderId, openReminders)
                }
                it.action == Intent.ACTION_MAIN -> {
                    Log.d(TAG, "App launched from home screen")
                }
                else -> {
                    Log.d(TAG, "Unknown intent action: ${it.action}")
                }
            }
        }
    }

    /**
     * Handle reminder-specific intent
     */
    private fun handleReminderIntent(reminderId: String?, openReminders: Boolean) {
        lifecycleScope.launch {
            try {
                reminderId?.let { id ->
                    // Mark reminder as triggered if needed
                    chatViewModel.markReminderTriggered(id)
                }

                // Navigate to reminders section if requested
                if (openReminders) {
                    // This will navigate to reminders in ChatFragment
                    // Implementation will be added when ChatFragment is ready
                    Log.d(TAG, "Would navigate to reminders section")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder intent: ${e.message}", e)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "Permission result received for request code: $requestCode")

        val result = permissionManager.handlePermissionResult(requestCode, permissions, grantResults)

        when (result.permissionGroup) {
            "notifications" -> {
                handleNotificationPermissionResult(result.isGranted)
            }
            "alarms" -> {
                Log.d(TAG, "Exact alarm permission result: ${result.isGranted}")
            }
        }

        // Show appropriate message to user
        if (!result.isGranted && result.shouldShowRationale) {
            showPermissionRationaleIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "New intent received")
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")

        // Refresh data when app comes to foreground
        lifecycleScope.launch {
            try {
                chatViewModel.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data on resume: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
    }

    /**
     * Get permission status for UI display
     */
    fun getPermissionStatus(): String {
        return try {
            permissionManager.getPermissionStatusMessage()
        } catch (e: Exception) {
            "Error al verificar permisos: ${e.message}"
        }
    }

    /**
     * Check if reminders can work
     */
    fun canRemindersWork(): Boolean {
        return try {
            permissionManager.canRemindersWork()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if reminders can work: ${e.message}")
            false
        }
    }

    /**
     * Request permissions from UI
     */
    fun requestPermissions() {
        try {
            permissionManager.requestAllPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions: ${e.message}")
        }
    }

    /**
     * Open app settings for manual permission management
     */
    fun openAppSettings() {
        try {
            permissionManager.openAppSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings: ${e.message}")
        }
    }
}