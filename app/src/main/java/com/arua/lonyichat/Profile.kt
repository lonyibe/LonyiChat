package com.arua.lonyichat.data

import com.google.gson.annotations.SerializedName

/**
 * Represents the full user profile fetched from the backend.
 * Uses the existing Timestamp model from Post.kt.
 */
data class Profile(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String?,
    val age: Int?,
    val country: String?,
    val photoUrl: String?,
    val followingCount: Int,
    val followerCount: Int,
    val churchCount: Int,
    @SerializedName("createdAt") val createdAt: Timestamp?
)

data class ProfileResponse(
    val success: Boolean,
    val profile: Profile
)