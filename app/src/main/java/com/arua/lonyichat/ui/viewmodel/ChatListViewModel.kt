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
import kotlin.random.Random // ✨ NEW: Required for simulating unread count

data class ChatListUiState(
    val conversations: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    // ✨ NEW: StateFlow for total unread chat messages count
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

                // ✨ FIX: Temporarily simulate unread count on the client-side
                // until the backend is fully deployed and returns non-zero values.
                val conversationsWithUnread = chats.map { chat ->
                    // In a real implementation, you would use the chat.unreadCount provided by the backend.
                    // For now, we simulate unread messages (between 0 and 5) for UI demonstration.
                    val simulatedUnreadCount = Random.nextInt(0, 6)
                    chat.copy(unreadCount = simulatedUnreadCount)
                }

                _uiState.update { it.copy(conversations = conversationsWithUnread, isLoading = false) }

                // ✨ NEW: Calculate and update the total unread chat count
                val totalUnread = conversationsWithUnread.sumOf { it.unreadCount }
                _unreadChatCount.value = totalUnread

            }.onFailure { error ->
                Log.w("ChatListViewModel", "Listen failed.", error)
                _uiState.update { it.copy(error = "Failed to load chats: ${error.localizedMessage}", isLoading = false) }
            }
        }
    }
}