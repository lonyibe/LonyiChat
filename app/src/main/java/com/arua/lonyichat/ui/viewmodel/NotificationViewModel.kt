package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class NotificationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0) // ADDED
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow() // ADDED

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getNotifications()
                .onSuccess { notifications ->
                    _uiState.update { it.copy(isLoading = false, notifications = notifications) }
                    _unreadCount.value = notifications.count { !it.read } // ADDED: Calculate unread count
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                    // Keep existing unread count on fetch failure
                }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            // Optimistically update the UI
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false // ADDED: Check original state

            _uiState.update { currentState ->
                currentState.copy(
                    notifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(read = true) else it
                    }
                )
            }
            if (wasUnread) _unreadCount.update { it - 1 } // ADDED: Decrement unread count

            // Make the API call
            ApiService.markNotificationAsRead(notificationId).onFailure {
                // If the API call fails, we could optionally roll back the change,
                // but for a "read" status, it's often acceptable to let it be.
                // For a more robust solution, you'd re-fetch or revert the state.
            }
        }
    }

    // ADDED: Friend Request Action Handlers
    fun acceptFriendRequest(notificationId: String, senderId: String) { // ADDED
        viewModelScope.launch { // ADDED
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false // ADDED

            // Optimistically update the UI: mark as read and change type to indicate action // ADDED
            _uiState.update { currentState -> // ADDED
                currentState.copy( // ADDED
                    notifications = currentState.notifications.map { // ADDED
                        if (it.id == notificationId) it.copy(read = true, type = "friend_accepted") else it // ADDED
                    } // ADDED
                ) // ADDED
            } // ADDED
            if (wasUnread) _unreadCount.update { it - 1 } // ADDED

            // Call API to accept (toggle follow from recipient to sender) and mark notification as read // ADDED
            ApiService.acceptFriendRequest(senderId) // ADDED
                .onSuccess { // ADDED
                    // Mark notification as read on the server to prevent re-fetching (safe to ignore failure) // ADDED
                    ApiService.markNotificationAsRead(notificationId) // ADDED
                } // ADDED
                .onFailure { error -> // ADDED
                    _uiState.update { it.copy(error = "Failed to accept friend request: ${error.localizedMessage}") } // ADDED
                } // ADDED
        } // ADDED
    } // ADDED

    fun deleteFriendRequest(notificationId: String) { // ADDED
        viewModelScope.launch { // ADDED
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false // ADDED

            // Optimistically update the UI: mark as read and change type to indicate dismissal // ADDED
            _uiState.update { currentState -> // ADDED
                currentState.copy( // ADDED
                    notifications = currentState.notifications.map { // ADDED
                        if (it.id == notificationId) it.copy(read = true, type = "friend_deleted") else it // ADDED
                    } // ADDED
                ) // ADDED
            } // ADDED
            if (wasUnread) _unreadCount.update { it - 1 } // ADDED

            // Call API to delete the request (mark notification as read on server) // ADDED
            ApiService.deleteFriendRequest(notificationId) // ADDED
                .onFailure { error -> // ADDED
                    _uiState.update { it.copy(error = "Failed to dismiss request: ${error.localizedMessage}") } // ADDED
                } // ADDED
        } // ADDED
    } // ADDED
}