package com.arua.lonyichat.data

import java.util.Date

// This is the correct definition that matches your ApiService
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Date
)