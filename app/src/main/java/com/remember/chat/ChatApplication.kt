package com.remember.chat

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Application class for the Remember chat app
 * Provides application-level context and initializes core components
 */
class ChatApplication : Application() {

    /**
     * Application scope for coroutines that should live as long as the application
     */
    val applicationScope = CoroutineScope(SupervisorJob())

    /**
     * Lazy initialization of the database
     */
    val database by lazy {
        com.remember.chat.database.AppDatabase.getDatabase(this, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize application components here
        initializeComponents()
    }

    /**
     * Initialize core application components
     */
    private fun initializeComponents() {
        // Initialize notification channels (will be implemented later)
        // Initialize other app-wide services

        // Log database statistics for debugging (remove in production)
        applicationScope.launch {
            try {
                val stats = database.getDatabaseStats()
                android.util.Log.d("ChatApplication", "Database initialized: ${stats.totalMessages} messages, ${stats.totalReminders} reminders")
            } catch (e: Exception) {
                android.util.Log.e("ChatApplication", "Error initializing database stats", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources if needed
    }
}