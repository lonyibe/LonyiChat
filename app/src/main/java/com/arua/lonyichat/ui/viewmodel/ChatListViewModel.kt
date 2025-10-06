// File Path: lonyibe/lonyichat/LonyiChat-554c58ac20d26ce973661057119b615306e7f3c8/app/src/main/java/com/arua/lonyichat/ui/viewmodel/ChatListViewModel.kt

package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Chat
import com.arua.lonyichat.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    private val _unreadChatCount = MutableStateFlow(0)
    val unreadChatCount: StateFlow<Int> = _unreadChatCount.asStateFlow()

    init {
        fetchConversations()

        SocketManager.chatBadgeUpdate
            .onEach {
                Log.d("ChatListViewModel", "Refreshing conversations due to socket event")
                fetchConversations()
            }
            .launchIn(viewModelScope)
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

                val totalUnread = chats.sumOf { it.unreadCount }
                _unreadChatCount.value = totalUnread

            }.onFailure { error ->
                Log.w("ChatListViewModel", "Listen failed.", error)
                _uiState.update { it.copy(error = "Failed to load chats: ${error.localizedMessage}", isLoading = false) }
            }
        }
    }
}