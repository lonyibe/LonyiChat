package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.Message
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.WebSocketEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class MessageUiState(
    val messages: List<Message> = emptyList(),
    // ✨ ADDED: Connection status for the UI
    val connectionStatus: String = "Connecting...",
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    // ✨ MODIFICATION: Job to manage the WebSocket connection lifecycle
    private var socketJob: Job? = null

    // This function is the main entry point to load a chat screen.
    // It fetches initial messages and then starts listening for real-time updates.
    fun loadMessages(chatId: String) {
        // Cancel any previous connection to avoid multiple listeners
        socketJob?.cancel()

        viewModelScope.launch {
            _uiState.value = MessageUiState(isLoading = true, connectionStatus = "Connecting...")
            try {
                // 1. Fetch the initial list of messages via HTTP
                val initialMessages = ApiService.getMessages(chatId)
                _uiState.value = _uiState.value.copy(messages = initialMessages, isLoading = false)

                // 2. Connect to the WebSocket and start listening for real-time events
                listenForUpdates(chatId)
            } catch (e: Exception) {
                _uiState.value = MessageUiState(error = "Failed to load messages: ${e.message}")
            }
        }
    }

    // ✨ NEW: Private function to handle collecting events from the WebSocket Flow
    private fun listenForUpdates(chatId: String) {
        // Launch a new coroutine to collect events from the WebSocket Flow
        socketJob = viewModelScope.launch {
            ApiService.chatSocketManager.connect(chatId)
                .catch { e ->
                    // Handle errors in the flow itself
                    _uiState.value = _uiState.value.copy(error = "WebSocket error: ${e.message}")
                }
                .collect { event ->
                    // Use a 'when' statement to handle each type of WebSocket event
                    when (event) {
                        is WebSocketEvent.NewMessage -> {
                            // Add the new message to the list if it's not already present
                            if (_uiState.value.messages.none { it.id == event.message.id }) {
                                _uiState.value = _uiState.value.copy(
                                    messages = _uiState.value.messages + event.message
                                )
                            }
                        }
                        is WebSocketEvent.MessageUpdated -> {
                            // Find and replace the updated message in the list
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.map {
                                    if (it.id == event.message.id) event.message else it
                                }
                            )
                        }
                        is WebSocketEvent.MessageDeleted -> {
                            // Filter out the deleted message from the list
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.filterNot { it.id == event.payload.messageId }
                            )
                        }
                        is WebSocketEvent.ConnectionOpened -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Connected")
                            Log.d("MessageViewModel", "WebSocket Connected.")
                        }
                        is WebSocketEvent.ConnectionClosed -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Disconnected")
                            Log.d("MessageViewModel", "WebSocket Disconnected.")
                        }
                        is WebSocketEvent.ConnectionFailed -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Error", error = event.error)
                            Log.e("MessageViewModel", "WebSocket Error: ${event.error}")
                        }
                        // Ignore church messages in this private chat ViewModel
                        is WebSocketEvent.NewChurchMessage,
                        is WebSocketEvent.ChurchMessageUpdated,
                        is WebSocketEvent.ChurchMessageDeleted -> {
                            // Do nothing
                        }
                    }
                }
        }
    }

    fun sendMessage(chatId: String, text: String, repliedToMessageId: String? = null, repliedToMessageContent: String? = null) {
        viewModelScope.launch {
            try {
                // The message is sent via HTTP. The server will broadcast it back via WebSocket,
                // and our `listenForUpdates` collector will handle adding it to the UI.
                ApiService.sendMessage(chatId, text, type = "text", repliedToMessageId = repliedToMessageId, repliedToMessageContent = repliedToMessageContent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
            }
        }
    }

    fun sendMediaMessage(chatId: String, uri: Uri, type: String, context: Activity) {
        viewModelScope.launch {
            // Keep isLoading false for sending, or add a specific 'isSending' state
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
                        // Like sendMessage, the update will arrive via WebSocket
                        ApiService.sendMessage(chatId, messageText, type, url)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(error = "Failed to send media message: ${e.message}")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "Upload failed: ${it.message}")
                }
            )
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            try {
                ApiService.editMessage(messageId, newContent)
                // The update will come through the 'message_updated' WebSocket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to edit message: ${e.message}")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                ApiService.deleteMessage(messageId)
                // The update will come through the 'message_deleted' WebSocket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete message: ${e.message}")
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                ApiService.reactToMessage(messageId, emoji)
                // The update will come through the 'message_updated' WebSocket event
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to react to message: ${e.message}")
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        // ✨ MODIFICATION: Clean up the coroutine job and disconnect the socket
        Log.d("MessageViewModel", "ViewModel cleared. Disconnecting socket.")
        socketJob?.cancel()
        ApiService.chatSocketManager.disconnect()
    }
}