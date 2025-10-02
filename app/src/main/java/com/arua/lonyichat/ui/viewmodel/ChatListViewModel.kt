package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.Chat
import com.arua.lonyichat.data.ApiService // 🔥 ADDED
import com.arua.lonyichat.data.ApiException
// REMOVED all Firebase imports
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState

    // REMOVED Firebase properties

    init {
        fetchConversations()
    }

    // ✨ FIX: Changed visibility from private to public to enable pull-to-refresh from UI
    fun fetchConversations() {
        val currentUserId = ApiService.getCurrentUserId()
        if (currentUserId == null) {
            _uiState.value = ChatListUiState(error = "User not logged in.")
            return
        }

        _uiState.value = ChatListUiState(isLoading = true)

        // 🔥 DETACHMENT: Replaced Firestore listener with a one-time API call
        viewModelScope.launch {
            ApiService.getChatConversations().onSuccess { chats ->
                _uiState.value = ChatListUiState(conversations = chats)
            }.onFailure { error ->
                Log.w("ChatListViewModel", "Listen failed.", error)
                _uiState.value = ChatListUiState(error = "Failed to load chats: ${error.localizedMessage}")
            }
        }
    }
}