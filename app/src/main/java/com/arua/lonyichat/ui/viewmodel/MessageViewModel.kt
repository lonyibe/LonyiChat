package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.Message
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class MessageUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()
    private var socket: Socket? = null

    init {
        try {
            socket = IO.socket(ApiService.BASE_URL)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    fun connectToChat(chatId: String) {
        socket?.on(Socket.EVENT_CONNECT) {
            socket?.emit("join_chat", chatId)
        }
        socket?.on("new_message") { args ->
            val data = args[0] as JSONObject
            val timestampString = data.getString("timestamp")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(timestampString)

            val newMessage = Message(
                id = data.getString("id"),
                chatId = data.getString("chatId"),
                senderId = data.getString("senderId"),
                senderName = data.getString("senderName"),
                senderPhotoUrl = data.optString("senderPhotoUrl", null),
                text = data.getString("text"),
                timestamp = date ?: Date(),
                type = data.optString("type", "text"), // ✨ HANDLE MESSAGE TYPE
                url = data.optString("url", null)      // ✨ HANDLE MEDIA URL
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + newMessage
            )
        }
        socket?.connect()
    }

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            _uiState.value = MessageUiState(isLoading = true)
            try {
                val messages = ApiService.getMessages(chatId)
                _uiState.value = MessageUiState(messages = messages)
                connectToChat(chatId)
            } catch (e: Exception) {
                _uiState.value = MessageUiState(error = "Failed to load messages: ${e.message}")
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        viewModelScope.launch {
            try {
                // ✨ SPECIFY THE MESSAGE TYPE AS 'TEXT'
                ApiService.sendMessage(chatId, text, type = "text")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
            }
        }
    }

    // ✨ NEW: Function to handle sending media messages
    fun sendMediaMessage(chatId: String, uri: Uri, type: String, context: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true) // Optional: Show a loading indicator
            val uploadResult = ApiService.uploadChatMedia(uri, context)
            uploadResult.fold(
                onSuccess = { url ->
                    // Set a default message text based on the media type
                    val messageText = when (type) {
                        "image" -> "📷 Image"
                        "video" -> "📹 Video"
                        "audio" -> "🎵 Audio"
                        "voice" -> "🎤 Voice Message"
                        else -> "Attachment"
                    }
                    try {
                        ApiService.sendMessage(chatId, messageText, type, url)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "Upload failed: ${it.message}")
                }
            )
            _uiState.value = _uiState.value.copy(isLoading = false) // Hide loading indicator
        }
    }


    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
    }
}