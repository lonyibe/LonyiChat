package com.arua.lonyichat.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// Represents a Church object from your backend
@Parcelize
data class Church(
    @SerializedName("_id") val id: String = "", // FIX: Added default value for safer Parcelable init
    val name: String = "", // FIX: Added default value
    val description: String = "", // FIX: Added default value
    val createdBy: String = "", // FIX: Added default value
    val members: List<String>,
    val followerCount: Int,
    // ✨ ADDED: New field for the Church's profile picture URL
    val photoUrl: String? = null
) : Parcelable

// Wrapper for the API response when fetching churches
data class ChurchResponse(
    val success: Boolean,
    val churches: List<Church>
)

// Wrapper for single church response (e.g., after creating one)
data class SingleChurchResponse(
    val success: Boolean,
    val church: Church
)