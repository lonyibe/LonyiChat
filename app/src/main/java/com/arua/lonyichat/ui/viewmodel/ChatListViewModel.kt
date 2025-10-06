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
import io.socket.client.IO // ADDED
import io.socket.client.Socket // ADDED
import java.net.URISyntaxException // ADDED

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

    // ADDED START: Socket.IO Setup for real-time updates
    private var socket: Socket? = null
    private val socketTag = "ChatListSocket"

    init {
        try {
            socket = IO.socket(ApiService.BASE_URL)
        } catch (e: URISyntaxException) {
            Log.e(socketTag, "Error creating socket URI", e)
        }

        setupSocketListener() // ADDED: Setup listener
        fetchConversations()
    }

    private fun setupSocketListener() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d(socketTag, "Socket Connected. Registering user...")
            // Register the user ID with the backend to receive private notifications
            ApiService.getCurrentUserId()?.let { userId ->
                socket?.emit("register", userId)
            }
        }

        // ADDED: Listener for the badge update event from the backend
        socket?.on("chat_badge_update") { args ->
            Log.d(socketTag, "Received chat_badge_update event. Refetching conversations...")
            // The backend signaled a new message arrived in one of our chats.
            // A full fetch is necessary to get the new unread count from the server.
            fetchConversations()
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d(socketTag, "Socket Disconnected")
        }

        socket?.connect() // ADDED: Connect the socket
    }

    // ADDED: Clear socket resources on ViewModel destruction
    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off("chat_badge_update")
        socket?.off(Socket.EVENT_DISCONNECT)
    }
    // ADDED END

    fun fetchConversations() {
        val currentUserId = ApiService.getCurrentUserId()
        if (currentUserId == null) {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        // ADDED: Ensure user registration on every fetch call if the socket is not connected (robustness)
        if (socket?.connected() == false) {
            socket?.connect()
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