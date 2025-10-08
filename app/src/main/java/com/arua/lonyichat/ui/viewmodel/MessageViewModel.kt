package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    // ✨ Helper to parse JSON objects safely
    private val gson = Gson()

    init {
        try {
            // ✨ It's better to use the ApiService's BASE_URL for consistency
            socket = IO.socket(ApiService.BASE_URL)
        } catch (e: URISyntaxException) {
            _uiState.value = _uiState.value.copy(error = "Failed to initialize socket: ${e.message}")
        }
    }

    // ✨ Function to parse a message from a JSONObject
    private fun parseMessageFromJson(data: JSONObject): Message {
        val timestampString = data.getString("timestamp")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = format.parse(timestampString) ?: Date()

        val reactionsJson = data.optJSONObject("reactions")
        val reactionsMapType = object : TypeToken<Map<String, List<String>>>() {}.type
        val reactions: Map<String, List<String>> = if (reactionsJson != null) {
            gson.fromJson(reactionsJson.toString(), reactionsMapType)
        } else {
            emptyMap()
        }

        return Message(
            id = data.getString("id"),
            chatId = data.getString("chatId"),
            senderId = data.getString("senderId"),
            senderName = data.getString("senderName"),
            senderPhotoUrl = data.optString("senderPhotoUrl", null),
            text = data.getString("text"),
            timestamp = date,
            type = data.optString("type", "text"),
            url = data.optString("url", null),
            isEdited = data.optBoolean("isEdited", false),
            repliedToMessageId = data.optString("repliedToMessageId", null),
            repliedToMessageContent = data.optString("repliedToMessageContent", null),
            reactions = reactions
        )
    }


    fun connectToChat(chatId: String) {
        // ✨ Clear existing listeners before adding new ones to prevent duplicates
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off("new_message")
        socket?.off("message_updated")
        socket?.off("message_deleted")

        socket?.on(Socket.EVENT_CONNECT) {
            socket?.emit("join_chat", chatId)
        }

        socket?.on("new_message") { args ->
            val data = args[0] as JSONObject
            val newMessage = parseMessageFromJson(data)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + newMessage
            )
        }

        // ✨ ADDED: Listener for real-time message updates (edits, reactions)
        socket?.on("message_updated") { args ->
            val data = args[0] as JSONObject
            val updatedMessage = parseMessageFromJson(data)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == updatedMessage.id) updatedMessage else it
                }
            )
        }

        // ✨ ADDED: Listener for real-time message deletions
        socket?.on("message_deleted") { args ->
            val data = args[0] as JSONObject
            val messageId = data.getString("messageId")
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.filterNot { it.id == messageId }
            )
        }

        if (socket?.connected() == false) {
            socket?.connect()
        }
    }


    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            _uiState.value = MessageUiState(isLoading = true)
            try {
                val messages = ApiService.getMessages(chatId)
                _uiState.value = MessageUiState(messages = messages)
                // Always ensure connection is established after loading initial messages
                connectToChat(chatId)
            } catch (e: Exception) {
                _uiState.value = MessageUiState(error = "Failed to load messages: ${e.message}")
            }
        }
    }

    // ✨ UPDATED: sendMessage now handles replies
    fun sendMessage(chatId: String, text: String, repliedToMessageId: String? = null, repliedToMessageContent: String? = null) {
        viewModelScope.launch {
            try {
                // The API call is now suspended, so the new message will arrive via WebSocket.
                // No need to manually add it to the state here.
                ApiService.sendMessage(chatId, text, type = "text", repliedToMessageId = repliedToMessageId, repliedToMessageContent = repliedToMessageContent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
            }
        }
    }

    fun sendMediaMessage(chatId: String, uri: Uri, type: String, context: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val uploadResult = ApiService.uploadChatMedia(uri, context)
            uploadResult.fold(
                onSuccess = { url ->
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
                        _uiState.value = _uiState.value.copy(error = "Failed to send media message: ${e.message}")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "Upload failed: ${it.message}")
                }
            )
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    // ✨ ADDED: Function to edit a message
    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            try {
                ApiService.editMessage(messageId, newContent)
                // The update will come through the 'message_updated' socket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to edit message: ${e.message}")
            }
        }
    }

    // ✨ ADDED: Function to delete a message
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                ApiService.deleteMessage(messageId)
                // The update will come through the 'message_deleted' socket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete message: ${e.message}")
            }
        }
    }

    // ✨ ADDED: Function to react to a message
    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                ApiService.reactToMessage(messageId, emoji)
                // The update will come through the 'message_updated' socket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to react to message: ${e.message}")
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
        // ✨ Clear listeners to avoid memory leaks
        socket?.off()
    }
}