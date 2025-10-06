package com.arua.lonyichat.data

// 🔥 REMOVED: import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.google.gson.annotations.SerializedName

// Represents a chat conversation document in MongoDB
data class Chat(
    // We expect the MongoDB _id to be serialized as 'id' by Gson
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    // 🔥 RENAMED/FIXED: Use SerializedName to map the backend MongoDB field name
    @SerializedName("lastMessageTimestamp")
    val lastMessageTimestamp: Date? = null,

    // We'll add this field to make querying easier
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotoUrls: Map<String, String?> = emptyMap()
)