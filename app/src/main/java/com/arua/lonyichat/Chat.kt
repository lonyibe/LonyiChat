package com.arua.lonyichat.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Represents a chat conversation document in Firestore
data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    @ServerTimestamp
    val lastMessageTimestamp: Date? = null,
    // We'll add this field to make querying easier
    val participantNames: Map<String, String> = emptyMap()
)