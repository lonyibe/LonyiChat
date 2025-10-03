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

    fun sendMessage(churchId: String, content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // Optimistic update
            val currentUser = ApiService.getCurrentUserId() ?: return@launch
            // Create a temporary message to show immediately
            // Note: This won't have the final ID or timestamp from the server
            // A more robust solution might use a "sending" state for the message

            ApiService.postChurchMessage(churchId, content)
                .onSuccess { newMessage ->
                    _uiState.update { currentState ->
                        currentState.copy(messages = currentState.messages + newMessage)
                    }
                }
                .onFailure { error ->
                    // Here you could implement a retry mechanism or show an error state
                    // For now, we'll just log it and update the UI with an error
                    _uiState.update { it.copy(error = "Failed to send message: ${error.localizedMessage}") }
                }
        }
    }
}