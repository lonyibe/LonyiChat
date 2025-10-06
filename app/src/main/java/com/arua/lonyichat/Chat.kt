package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName
import java.util.Date

// Represents a chat conversation document in MongoDB
data class Chat(
    // We expect the MongoDB _id to be serialized as 'id' by Gson
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    // Use SerializedName to map the backend MongoDB field name
    @SerializedName("lastMessageTimestamp")
    val lastMessageTimestamp: Date? = null,

    // These fields are populated by the backend to make displaying info easier
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotoUrls: Map<String, String?> = emptyMap(),
    // ✨ NEW: Field for unread message count from the backend
    val unreadCount: Int = 0
)

// Wrapper for the API response when fetching chats
data class ChatConversationsResponse(val success: Boolean, val chats: List<Chat>)