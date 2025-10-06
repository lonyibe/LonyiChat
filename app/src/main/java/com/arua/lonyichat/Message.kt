// app/src/main/java/com/arua/lonyichat/Message.kt

package com.arua.lonyichat

import java.util.Date

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val text: String,
    val timestamp: Date,
    val type: String = "text", // Can be "text", "image", "video", "audio", or "voice"
    val url: String? = null // URL for media content
)