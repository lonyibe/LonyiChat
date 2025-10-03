package com.arua.lonyichat.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Event(
    @SerializedName("_id") val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    // CRITICAL FIX: Ensure the correct use-site target is explicitly set for the property backing field.
    @field:RawValue val date: Timestamp,
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