package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName
import java.util.Date

// Represents the structure of a Post from your backend API
data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val imageUrl: String?, // ✨ ADDED THIS LINE
    val type: String, // 'post' or 'status'
    val reactions: Reactions,
    val commentCount: Int,
    // Using SerializedName to match the JSON key from the backend
    @SerializedName("createdAt") val createdAt: Timestamp
)

data class Reactions(
    val amen: Int = 0,
    val hallelujah: Int = 0,
    val praiseGod: Int = 0
)

// Helper class to parse Firestore's timestamp object
data class Timestamp(
    @SerializedName("_seconds") val seconds: Long,
    @SerializedName("_nanoseconds") val nanoseconds: Int
) {
    fun toDate(): Date = Date(seconds * 1000)
}

// Wrapper class to match the API's top-level response structure
data class PostResponse(
    val success: Boolean,
    val posts: List<Post>
)