package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName

// Represents a single notification item from your backend
// ✨ FIX: Added default values to all fields to make the class more robust
// and prevent deserialization crashes if a field is missing from the API response.
data class Notification(
    @SerializedName("_id") val id: String = "",
    val recipient: String = "",
    val sender: Sender? = null, // Made nullable in case the sender's account is deleted
    val type: String = "", // 'reaction', 'comment', 'event', 'friend_request'
    val resourceId: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp? = null // Made nullable for safety
)

// Represents the nested 'sender' object in the notification
data class Sender(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val photoUrl: String? = null
)

// Wrapper for the full API response
data class NotificationResponse(
    val success: Boolean = false,
    val notifications: List<Notification> = emptyList()
)
