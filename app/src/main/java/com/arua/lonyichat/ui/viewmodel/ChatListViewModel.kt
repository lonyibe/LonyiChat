package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.Chat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    init {
        fetchConversations()
    }

    private fun fetchConversations() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _uiState.value = ChatListUiState(error = "User not logged in.")
            return
        }

        _uiState.value = ChatListUiState(isLoading = true)

        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != nil) {
                    Log.w("ChatListViewModel", "Listen failed.", e)
                    _uiState.value = ChatListUiState(error = "Failed to load chats.")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val chats = snapshots.toObjects(Chat::class.java).mapIndexed { index, chat ->
                        chat.copy(id = snapshots.documents[index].id)
                    }
                    _uiState.value = ChatListUiState(conversations = chats)
                }
            }
    }
}