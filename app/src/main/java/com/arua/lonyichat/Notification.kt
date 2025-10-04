package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName

// Represents a single notification item from your backend
data class Notification(
    @SerializedName("_id") val id: String,
    val recipient: String,
    val sender: Sender,
    val type: String, // 'reaction', 'comment', 'event', 'friend_request'
    val resourceId: String,
    val read: Boolean,
    val createdAt: Timestamp
)

// Represents the nested 'sender' object in the notification
data class Sender(
    @SerializedName("_id") val id: String,
    val name: String,
    val photoUrl: String?
)

// Wrapper for the full API response (This is the correct location)
data class NotificationResponse(
    val success: Boolean,
    val notifications: List<Notification>
)