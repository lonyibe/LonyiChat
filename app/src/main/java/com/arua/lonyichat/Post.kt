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
    val imageUrl: String?,
    val type: String, // 'post' or 'status'
    val reactions: Reactions,
    val userReactions: UserReactions, // ✨ ADDED: To track if the current user has reacted
    val commentCount: Int,
    val shareCount: Int,
    @SerializedName("createdAt") val createdAt: Timestamp
)

// Holds the counts of each reaction type
data class Reactions(
    val amen: Int = 0,
    val hallelujah: Int = 0,
    val praiseGod: Int = 0
)

// ✨ ADDED: Holds the boolean state of the current user's reactions
data class UserReactions(
    val amen: Boolean = false,
    val hallelujah: Boolean = false,
    val praiseGod: Boolean = false
)

data class Comment(
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val createdAt: Timestamp
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

// ✨ ADDED: Wrapper for a single post response (for creating a new post)
data class SinglePostResponse(
    val success: Boolean,
    val post: Post
)