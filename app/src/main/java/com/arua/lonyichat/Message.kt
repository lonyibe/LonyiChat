// app/src/main/java/com/arua/lonyichat/Message.kt

package com.arua.lonyichat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Parcelize
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val text: String,
    // ✨ MODIFIED: Changed from Date to String to match server's format
    val timestamp: String,
    val type: String = "text",
    val url: String? = null,
    val isEdited: Boolean = false,
    val repliedToMessageId: String? = null,
    val repliedToMessageContent: String? = null,
    val reactions: Map<String, List<String>> = emptyMap()
) : Parcelable {
    // ✨ ADDED: Helper function to safely parse the timestamp string into a Date object
    fun getTimestampDate(): Date? {
        return try {
            // ISO 8601 format from the server: "2025-10-10T10:11:50.322Z"
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(timestamp)
        } catch (e: Exception) {
            // Return null if parsing fails, preventing a crash
            null
        }
    }
}