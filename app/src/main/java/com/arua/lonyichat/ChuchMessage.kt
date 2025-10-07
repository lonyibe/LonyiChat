package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName

data class ChurchMessage(
    @SerializedName("_id") val id: String,
    val churchId: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val createdAt: Timestamp,
    // ✨ ADDED for new features
    val repliedToMessageId: String? = null,
    val repliedToMessageContent: String? = null,
    // ✨ FIX: Made nullable to prevent NullPointerException when map is omitted in JSON response
    val reactions: Map<String, List<String>>? = null, // Map of reaction emoji -> list of userIds
    val isEdited: Boolean = false
)

data class ChurchMessagesResponse(
    val success: Boolean,
    val messages: List<ChurchMessage>
)

data class SingleChurchMessageResponse(
    val success: Boolean,
    val message: ChurchMessage
)
