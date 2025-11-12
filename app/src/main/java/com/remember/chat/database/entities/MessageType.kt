package com.remember.chat.database.entities

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Enum representing different types of messages in the chat
 */
enum class MessageType {
    SENT,
    RECEIVED,
    REMINDER
}

/**
 * Type converter for MessageType enum to work with Room database
 */
class MessageTypeConverter {
    @TypeConverter
    fun fromMessageType(messageType: MessageType): String {
        return messageType.name
    }

    @TypeConverter
    fun toMessageType(messageType: String): MessageType {
        return MessageType.valueOf(messageType)
    }
}