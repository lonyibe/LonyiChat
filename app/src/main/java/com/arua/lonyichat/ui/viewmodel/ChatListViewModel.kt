package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.Chat
import com.arua.lonyichat.data.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        fetchConversations()
    }

    fun fetchConversations() {
        val currentUserId = ApiService.getCurrentUserId()
        if (currentUserId == null) {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            ApiService.getChatConversations().onSuccess { chats ->
                _uiState.update { it.copy(conversations = chats, isLoading = false) }
            }.onFailure { error ->
                Log.w("ChatListViewModel", "Listen failed.", error)
                _uiState.update { it.copy(error = "Failed to load chats: ${error.localizedMessage}", isLoading = false) }
            }
        }
    }
}