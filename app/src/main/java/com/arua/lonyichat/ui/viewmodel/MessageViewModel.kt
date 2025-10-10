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
    val connectionStatus: String = "Connecting...",
    // ✨ ADDED: A map to store users who are currently typing (userId to username)
    val typingUsers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    private var socketJob: Job? = null

    fun loadMessages(chatId: String) {
        socketJob?.cancel()

        viewModelScope.launch {
            _uiState.value = MessageUiState(isLoading = true, connectionStatus = "Connecting...")
            try {
                val initialMessages = ApiService.getMessages(chatId)
                _uiState.value = _uiState.value.copy(messages = initialMessages, isLoading = false)
                listenForUpdates(chatId)
            } catch (e: Exception) {
                _uiState.value = MessageUiState(error = "Failed to load messages: ${e.message}")
            }
        }
    }

    private fun listenForUpdates(chatId: String) {
        socketJob = viewModelScope.launch {
            ApiService.chatSocketManager.connect(chatId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "WebSocket error: ${e.message}")
                }
                .collect { event ->
                    when (event) {
                        is WebSocketEvent.NewMessage -> {
                            if (_uiState.value.messages.none { it.id == event.message.id }) {
                                _uiState.value = _uiState.value.copy(
                                    messages = _uiState.value.messages + event.message
                                )
                            }
                        }
                        is WebSocketEvent.MessageUpdated -> {
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.map {
                                    if (it.id == event.message.id) event.message else it
                                }
                            )
                        }
                        is WebSocketEvent.MessageDeleted -> {
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.filterNot { it.id == event.payload.messageId }
                            )
                        }
                        // ✨ ADDED: Handle typing events
                        is WebSocketEvent.UserTyping -> {
                            val newTypingUsers = _uiState.value.typingUsers.toMutableMap()
                            newTypingUsers[event.payload.userId] = event.payload.username
                            _uiState.value = _uiState.value.copy(typingUsers = newTypingUsers)
                        }
                        is WebSocketEvent.UserStoppedTyping -> {
                            val newTypingUsers = _uiState.value.typingUsers.toMutableMap()
                            newTypingUsers.remove(event.payload.userId)
                            _uiState.value = _uiState.value.copy(typingUsers = newTypingUsers)
                        }
                        is WebSocketEvent.ConnectionOpened -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Connected")
                            Log.d("MessageViewModel", "WebSocket Connected.")
                        }
                        is WebSocketEvent.ConnectionClosed -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Disconnected", typingUsers = emptyMap()) // Clear typing users on disconnect
                            Log.d("MessageViewModel", "WebSocket Disconnected.")
                        }
                        is WebSocketEvent.ConnectionFailed -> {
                            _uiState.value = _uiState.value.copy(connectionStatus = "Error", error = event.error)
                            Log.e("MessageViewModel", "WebSocket Error: ${event.error}")
                        }
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
                ApiService.sendMessage(chatId, text, type = "text", repliedToMessageId = repliedToMessageId, repliedToMessageContent = repliedToMessageContent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
            }
        }
    }

    // ✨ ADDED: Functions to notify the server about typing status
    fun sendTyping(chatId: String) {
        viewModelScope.launch {
            ApiService.chatSocketManager.sendTyping(chatId)
        }
    }

    fun sendStopTyping(chatId: String) {
        viewModelScope.launch {
            ApiService.chatSocketManager.sendStopTyping(chatId)
        }
    }


    fun sendMediaMessage(chatId: String, uri: Uri, type: String, context: Activity) {
        viewModelScope.launch {
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
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            try {
                ApiService.editMessage(messageId, newContent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to edit message: ${e.message}")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                ApiService.deleteMessage(messageId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete message: ${e.message}")
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                ApiService.reactToMessage(messageId, emoji)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to react to message: ${e.message}")
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        Log.d("MessageViewModel", "ViewModel cleared. Disconnecting socket.")
        socketJob?.cancel()
        ApiService.chatSocketManager.disconnect()
    }
}