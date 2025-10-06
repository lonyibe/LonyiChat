// app/src/main/java/com/arua/lonyichat/Message.kt

package com.arua.lonyichat

import java.util.Date

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String, // Added
    val senderPhotoUrl: String?, // Added
    val text: String,
    val timestamp: Date
)