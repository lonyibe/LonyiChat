package com.arua.lonyichat.data

import android.os.Parcelable
// import com.google.gson.annotations.SerializedName // REMOVED: No longer needed after fixing the field name below
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Event(
    // FIX: Removed @SerializedName("_id"). Gson will now correctly map the backend's "id" field
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    // FIX: Correct Parcelize usage for Timestamp
    val date: @RawValue Timestamp,
    val location: String,
    val createdBy: String,
    val authorName: String,
    val authorPhotoUrl: String?
) : Parcelable

// Wrapper for the API response when fetching a list of events
data class EventResponse(
    val success: Boolean,
    val events: List<Event>
)

// Wrapper for single event response
data class SingleEventResponse(
    val success: Boolean,
    val event: Event
)