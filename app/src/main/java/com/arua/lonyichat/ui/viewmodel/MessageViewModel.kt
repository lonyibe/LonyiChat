package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            _uiState.value = MessageUiState(isLoading = true)
            try {
                // This is a placeholder. We will implement real-time updates later.
                val messages = ApiService.getMessages(chatId)
                _uiState.value = MessageUiState(messages = messages.sortedByDescending { it.timestamp })
            } catch (e: Exception) {
                _uiState.value = MessageUiState(error = "Failed to load messages: ${e.message}")
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        viewModelScope.launch {
            try {
                val newMessage = ApiService.sendMessage(chatId, text)
                // Add the new message to the top of the list
                val currentMessages = _uiState.value.messages
                _uiState.value = _uiState.value.copy(messages = listOf(newMessage) + currentMessages)
            } catch (e: Exception) {
                // Handle error, maybe show a toast or a snackbar
                _uiState.value = _uiState.value.copy(error = "Failed to send message: ${e.message}")
            }
        }
    }
}