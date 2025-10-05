package com.arua.lonyichat.data

// Represents a single Media item from your API
// Can be a video, livestream, or testimony
data class MediaItem(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val mediaType: String, // 'video', 'livestream', or 'testimony'
    val uploaderId: String,
    // ✨ ADDED: Real-time interaction fields from backend ✨
    val uploaderName: String,
    val uploaderPhotoUrl: String?,
    val likes: Int = 0,
    val comments: Int = 0,
    val shareCount: Int = 0,
    val downloadCount: Int = 0,
    val isLiked: Boolean = false // Current user's like status
)

// Wrapper for the API response
data class MediaResponse(
    val success: Boolean,
    val media: List<MediaItem>
)