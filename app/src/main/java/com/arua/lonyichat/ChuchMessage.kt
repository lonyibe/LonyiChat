package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName

data class ChurchMessage(
    @SerializedName("_id") val id: String,
    val churchId: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val createdAt: Timestamp
)

data class ChurchMessagesResponse(
    val success: Boolean,
    val messages: List<ChurchMessage>
)

data class SingleChurchMessageResponse(
    val success: Boolean,
    val message: ChurchMessage
)