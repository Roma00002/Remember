package com.remember.chat.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.remember.chat.database.dao.MessageDao
import com.remember.chat.database.dao.ReminderDao
import com.remember.chat.database.entities.Message
import com.remember.chat.database.entities.MessageType
import com.remember.chat.database.entities.MessageTypeConverter
import com.remember.chat.database.entities.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room database for the chat application
 */
@Database(
    entities = [
        Message::class,
        Reminder::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(MessageTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Get the Message Data Access Object
     */
    abstract fun messageDao(): MessageDao

    /**
     * Get the Reminder Data Access Object
     */
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the database instance (singleton pattern)
         */
        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "remember_chat_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Reset the database instance (for testing purposes)
         */
        fun resetDatabase() {
            INSTANCE = null
        }
    }

    /**
     * Database callback for pre-populating data and setting up initial state
     */
    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        /**
         * Populate database with initial sample data (for demo purposes)
         */
        suspend fun populateDatabase(database: AppDatabase) {
            val messageDao = database.messageDao()
            val reminderDao = database.reminderDao()

            // Add sample welcome message
            val welcomeMessage = Message(
                content = "¡Bienvenido a Remember! Este es un chat estilo WhatsApp donde puedes programar recordatorios. 📅",
                type = MessageType.RECEIVED,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(welcomeMessage)

            // Add sample instruction message
            val instructionMessage = Message(
                content = "Para crear un recordatorio, haz clic en el botón del calendario 📅 junto al botón de enviar.",
                type = MessageType.RECEIVED,
                timestamp = System.currentTimeMillis() + 1000
            )
            messageDao.insertMessage(instructionMessage)

            // Add sample sent message
            val sampleSentMessage = Message(
                content = "Hola, esta es una aplicación de chat con recordatorios",
                type = MessageType.SENT,
                timestamp = System.currentTimeMillis() + 2000
            )
            messageDao.insertMessage(sampleSentMessage)
        }
    }

    /**
     * Clear all data from the database
     */
    suspend fun clearAllData() {
        messageDao().deleteAllMessages()
        reminderDao().deleteAllReminders()
    }

    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): DatabaseStats {
        val messageDao = messageDao()
        val reminderDao = reminderDao()

        return DatabaseStats(
            totalMessages = messageDao.getTotalMessageCount(),
            sentMessages = messageDao.getMessageCountByType(MessageType.SENT),
            receivedMessages = messageDao.getMessageCountByType(MessageType.RECEIVED),
            reminderMessages = messageDao.getMessageCountByType(MessageType.REMINDER),
            totalReminders = reminderDao.getReminderCount(),
            activeReminders = reminderDao.getActiveReminderCount(),
            triggeredReminders = reminderDao.getTriggeredReminderCount()
        )
    }

    /**
     * Data class representing database statistics
     */
    data class DatabaseStats(
        val totalMessages: Int,
        val sentMessages: Int,
        val receivedMessages: Int,
        val reminderMessages: Int,
        val totalReminders: Int,
        val activeReminders: Int,
        val triggeredReminders: Int
    ) {
        fun getFormattedStats(): String {
            return """
                📊 Estadísticas de la Base de Datos:

                Mensajes:
                • Total: $totalMessages
                • Enviados: $sentMessages
                • Recibidos: $receivedMessages
                • Recordatorios: $reminderMessages

                Recordatorios:
                • Total: $totalReminders
                • Activos: $activeReminders
                • Ejecutados: $triggeredReminders
            """.trimIndent()
        }
    }
}