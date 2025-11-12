package com.remember.chat.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.remember.chat.database.entities.MessageType
import com.remember.chat.database.entities.Reminder
import com.remember.chat.repository.ChatRepository
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for managing chat UI state and business logic
 * Handles message operations and reminder scheduling
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(
        com.remember.chat.ChatApplication().database
    )

    // ==================== LiveData ====================

    // Messages list
    private val _messages = MutableLiveData<List<com.remember.chat.database.entities.Message>>()
    val messages: LiveData<List<com.remember.chat.database.entities.Message>> = _messages

    // Loading states
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isSending = MutableLiveData<Boolean>()
    val isSending: LiveData<Boolean> = _isSending

    // Error handling
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // UI state
    private val _inputText = MutableLiveData<String>()
    val inputText: LiveData<String> = _inputText

    // Reminders
    private val _activeReminders = MutableLiveData<List<Reminder>>()
    val activeReminders: LiveData<List<Reminder>> = _activeReminders

    // Success message for operations
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    // ==================== Initialization ====================

    init {
        loadMessages()
        loadActiveReminders()
    }

    // ==================== Message Operations ====================

    /**
     * Load all messages from the database
     */
    private fun loadMessages() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.getAllMessages().collect { messageList ->
                    _messages.postValue(messageList)
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error al cargar los mensajes: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Send a new message
     */
    fun sendMessage(content: String) {
        val validation = repository.validateMessage(content)
        if (!validation.isValid) {
            _errorMessage.value = validation.errorMessage
            return
        }

        viewModelScope.launch {
            try {
                _isSending.value = true
                repository.sendMessage(content)
                _inputText.value = ""
                _successMessage.value = "Mensaje enviado"
            } catch (e: Exception) {
                _errorMessage.value = "Error al enviar mensaje: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * Schedule a new reminder
     */
    fun scheduleReminder(message: String, scheduledTime: Long) {
        val messageValidation = repository.validateMessage(message)
        if (!messageValidation.isValid) {
            _errorMessage.value = messageValidation.errorMessage
            return
        }

        val timeValidation = repository.validateReminderTime(scheduledTime)
        if (!timeValidation.isValid) {
            _errorMessage.value = timeValidation.errorMessage
            return
        }

        viewModelScope.launch {
            try {
                _isSending.value = true
                val (reminder, reminderMessage) = repository.scheduleReminderWithMessage(
                    message = message,
                    scheduledTime = scheduledTime
                )

                _successMessage.value = "Recordatorio programado para ${reminder.getFormattedScheduledTime()}"

                // Schedule the actual alarm (this will be implemented in ReminderManager)
                scheduleReminderAlarm(reminder)

            } catch (e: Exception) {
                _errorMessage.value = "Error al programar recordatorio: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * Delete a message
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(messageId)
                _successMessage.value = "Mensaje eliminado"
            } catch (e: Exception) {
                _errorMessage.value = "Error al eliminar mensaje: ${e.message}"
            }
        }
    }

    /**
     * Mark a reminder as triggered
     */
    fun markReminderTriggered(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.markReminderAsTriggered(reminderId)
                loadActiveReminders()
            } catch (e: Exception) {
                _errorMessage.value = "Error al actualizar recordatorio: ${e.message}"
            }
        }
    }

    // ==================== Reminder Operations ====================

    /**
     * Load active reminders
     */
    private fun loadActiveReminders() {
        viewModelScope.launch {
            try {
                repository.getActiveReminders().collect { reminders ->
                    _activeReminders.postValue(reminders)
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error al cargar recordatorios: ${e.message}")
            }
        }
    }

    /**
     * Cancel a reminder
     */
    fun cancelReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                // Get reminder details before cancelling
                val reminder = repository.getReminderById(reminderId)
                repository.deleteReminder(reminderId)

                // Cancel the alarm (this will be implemented in ReminderManager)
                cancelReminderAlarm(reminderId, reminder?.alarmId ?: 0)

                _successMessage.value = "Recordatorio cancelado"
                loadActiveReminders()
            } catch (e: Exception) {
                _errorMessage.value = "Error al cancelar recordatorio: ${e.message}"
            }
        }
    }

    /**
     * Reschedule a reminder
     */
    fun rescheduleReminder(reminderId: String, newScheduledTime: Long) {
        val timeValidation = repository.validateReminderTime(newScheduledTime)
        if (!timeValidation.isValid) {
            _errorMessage.value = timeValidation.errorMessage
            return
        }

        viewModelScope.launch {
            try {
                val reminder = repository.getReminderById(reminderId)
                if (reminder != null) {
                    // Cancel old alarm
                    cancelReminderAlarm(reminderId, reminder.alarmId)

                    // Update reminder time
                    repository.rescheduleReminder(reminderId, newScheduledTime)

                    // Schedule new alarm
                    val updatedReminder = reminder.copy(scheduledTime = newScheduledTime)
                    scheduleReminderAlarm(updatedReminder)

                    _successMessage.value = "Recordatorio reprogramado"
                } else {
                    _errorMessage.value = "Recordatorio no encontrado"
                }
                loadActiveReminders()
            } catch (e: Exception) {
                _errorMessage.value = "Error al reprogramar recordatorio: ${e.message}"
            }
        }
    }

    // ==================== UI State Management ====================

    /**
     * Update input text
     */
    fun updateInputText(text: String) {
        _inputText.value = text
    }

    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Refresh data
     */
    fun refresh() {
        loadMessages()
        loadActiveReminders()
    }

    // ==================== Alarm Scheduling (Placeholder) ====================

    /**
     * Schedule a reminder alarm (will be implemented in ReminderManager)
     */
    private fun scheduleReminderAlarm(reminder: Reminder) {
        // This will be implemented when we create ReminderManager
        // For now, just log the operation
        android.util.Log.d("ChatViewModel", "Scheduling alarm for reminder: ${reminder.message} at ${reminder.scheduledTime}")
    }

    /**
     * Cancel a reminder alarm (will be implemented in ReminderManager)
     */
    private fun cancelReminderAlarm(reminderId: String, alarmId: Int) {
        // This will be implemented when we create ReminderManager
        // For now, just log the operation
        android.util.Log.d("ChatViewModel", "Cancelling alarm for reminder: $reminderId")
    }

    // ==================== Utility Functions ====================

    /**
     * Get formatted time for display
     */
    fun getFormattedTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }

    /**
     * Get formatted date and time for display
     */
    fun getFormattedDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }

    /**
     * Check if a message is from today
     */
    fun isFromToday(timestamp: Long): Boolean {
        val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))
        return timestamp >= todayStart
    }

    /**
     * Get message count by type
     */
    fun getMessageCountByType(type: MessageType): LiveData<Int> = liveData {
        try {
            emit(repository.getMessageCount(type))
        } catch (e: Exception) {
            _errorMessage.postValue("Error al obtener conteo de mensajes: ${e.message}")
            emit(0)
        }
    }

    /**
     * Get next reminder time display
     */
    fun getNextReminderDisplay(): LiveData<String> = liveData {
        try {
            val nextReminder = repository.getNextReminder()
            val display = if (nextReminder != null) {
                "Próximo recordatorio: ${nextReminder.getTimeUntilDisplay()}"
            } else {
                "No hay recordatorios programados"
            }
            emit(display)
        } catch (e: Exception) {
            _errorMessage.postValue("Error al obtener próximo recordatorio: ${e.message}")
            emit("Error al cargar recordatorios")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
    }
}