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
// ✨ REMOVED: No longer need to import Random for simulating unread count
// import kotlin.random.Random

data class ChatListUiState(
    val conversations: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val _unreadChatCount = MutableStateFlow(0)
    val unreadChatCount: StateFlow<Int> = _unreadChatCount.asStateFlow()

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

                // ✨ FIX: Removed the simulation of the unread count.
                // The app will now use the actual `unreadCount` from the backend.
                _uiState.update { it.copy(conversations = chats, isLoading = false) }

                // ✨ NEW: Calculate and update the total unread chat count from the actual data
                val totalUnread = chats.sumOf { it.unreadCount }
                _unreadChatCount.value = totalUnread

            }.onFailure { error ->
                Log.w("ChatListViewModel", "Listen failed.", error)
                _uiState.update { it.copy(error = "Failed to load chats: ${error.localizedMessage}", isLoading = false) }
            }
        }
    }
}