package com.arua.lonyichat.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// Represents a Church object from your backend
@Parcelize
data class Church(
    @SerializedName("_id") val id: String,
    val name: String,
    val description: String,
    val createdBy: String,
    val members: List<String>,
    val followerCount: Int
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