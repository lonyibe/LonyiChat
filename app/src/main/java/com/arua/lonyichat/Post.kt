package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName
import java.util.Date

// ✨ NEW: Data classes to represent a Poll ✨
data class PollOption(
    @SerializedName("_id") val id: String,
    val option: String,
    val votes: List<String>
)

data class Poll(
    val options: List<PollOption>
)

// Represents the structure of a Post from your backend API
data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val imageUrl: String?,
    val type: String, // 'post', 'status', 'poll', 'prayer'
    val reactions: Reactions,
    val userReactions: UserReactions,
    val commentCount: Int,
    val shareCount: Int,
    @SerializedName("createdAt") val createdAt: Timestamp,
    val poll: Poll? // ✨ ADDED: Poll data is now included
)

// Holds the counts of each reaction type
data class Reactions(
    val amen: Int = 0,
    val hallelujah: Int = 0,
    val praiseGod: Int = 0,
    val praying: Int = 0 // ✨ ADDED: For prayer requests
)

// Holds the boolean state of the current user's reactions
data class UserReactions(
    val amen: Boolean = false,
    val hallelujah: Boolean = false,
    val praiseGod: Boolean = false,
    val praying: Boolean = false // ✨ ADDED: For prayer requests
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

// Wrapper for a single post response (for creating a new post)
data class SinglePostResponse(
    val success: Boolean,
    val post: Post
)

// Data models for fetching reactors list from the new API endpoint
data class Reactor(
    val userId: String,
    val name: String,
    val photoUrl: String?
)

data class ReactionsWithUsers(
    val amen: List<Reactor> = emptyList(),
    val hallelujah: List<Reactor> = emptyList(),
    val praiseGod: List<Reactor> = emptyList(),
    val praying: List<Reactor> = emptyList() // ✨ ADDED: Praying reactors
)

data class ReactorsResponse(
    val success: Boolean,
    val reactions: ReactionsWithUsers
)