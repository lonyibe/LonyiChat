package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.ChurchMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChurchChatUiState(
    val messages: List<ChurchMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChurchChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChurchChatUiState())
    val uiState: StateFlow<ChurchChatUiState> = _uiState.asStateFlow()

    fun fetchMessages(churchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getChurchMessages(churchId)
                .onSuccess { messages ->
                    _uiState.update { it.copy(isLoading = false, messages = messages) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                }
        }
    }

    // ✨ UPDATED: sendMessage to accept reply context
    fun sendMessage(
        churchId: String,
        content: String,
        repliedToMessageId: String? = null,
        repliedToMessageContent: String? = null
    ) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // Optimistic update logic is generally safer to skip in a remote chat for complexity reasons.
            // Relying on refresh/fetch.

            ApiService.postChurchMessage(churchId, content, repliedToMessageId, repliedToMessageContent)
                .onSuccess { newMessage ->
                    _uiState.update { currentState ->
                        currentState.copy(messages = currentState.messages + newMessage)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to send message: ${error.localizedMessage}") }
                }
        }
    }

    // ✨ NEW: React to a message
    fun reactToMessage(churchId: String, messageId: String, reactionEmoji: String) {
        viewModelScope.launch {
            ApiService.reactToChurchMessage(churchId, messageId, reactionEmoji)
                .onSuccess { updatedMessage ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            messages = currentState.messages.map { message ->
                                if (message.id == messageId) updatedMessage else message
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to add reaction: ${error.localizedMessage}") }
                }
        }
    }

    // ✨ NEW: Delete a message
    fun deleteMessage(churchId: String, messageId: String) {
        viewModelScope.launch {
            val originalMessages = _uiState.value.messages
            // Optimistic update: remove the message immediately
            _uiState.update { currentState ->
                currentState.copy(messages = currentState.messages.filterNot { it.id == messageId })
            }

            ApiService.deleteChurchMessage(churchId, messageId).onFailure { error ->
                // Rollback on failure
                _uiState.update {
                    it.copy(
                        messages = originalMessages,
                        error = "Failed to delete message: ${error.localizedMessage}"
                    )
                }
            }
        }
    }
}