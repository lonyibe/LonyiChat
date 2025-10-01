package com.arua.lonyichat.data

// Represents a Church object from your backend
data class Church(
    val id: String,
    val name: String,
    val description: String,
    val createdBy: String,
    val members: List<String>,
    val followerCount: Int
)

// Wrapper for the API response when fetching churches
data class ChurchResponse(
    val success: Boolean,
    val churches: List<Church>
)