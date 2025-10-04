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
import kotlinx.coroutines.SupervisorJob // ADDED
import kotlinx.coroutines.Job // ADDED
import kotlinx.coroutines.CoroutineScope // ADDED
import kotlinx.coroutines.joinAll // ADDED
import kotlinx.coroutines.async // ADDED

data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class NotificationViewModel : ViewModel() {
    // ADDED START: Coroutine Job tracking
    private val actionJobs = mutableListOf<Job>()
    private val actionScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())
    // ADDED END

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getNotifications()
                .onSuccess { notifications ->
                    _uiState.update { it.copy(isLoading = false, notifications = notifications) }
                    _unreadCount.value = notifications.count { !it.read }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                    // Keep existing unread count on fetch failure
                }
        }
    }

    fun markAsRead(notificationId: String) {
        val job = actionScope.launch { // CHANGED: Launch job in actionScope
            // Optimistically update the UI
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false

            _uiState.update { currentState ->
                currentState.copy(
                    notifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(read = true) else it
                    }
                )
            }
            if (wasUnread) _unreadCount.update { it - 1 }

            // Make the API call
            ApiService.markNotificationAsRead(notificationId).onFailure {
                // Failure handling is non-critical for read status
            }
        }
        actionJobs.add(job) // ADDED: Track job
        job.invokeOnCompletion { actionJobs.remove(job) } // ADDED: Remove job on completion
    }

    fun acceptFriendRequest(notificationId: String, senderId: String) {
        val job = actionScope.launch { // CHANGED: Launch job in actionScope
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false

            // Optimistically update the UI: mark as read and change type
            _uiState.update { currentState ->
                currentState.copy(
                    notifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(read = true, type = "friend_accepted") else it
                    }
                )
            }
            if (wasUnread) _unreadCount.update { it - 1 }

            // Call API to accept (toggle follow from recipient to sender) and mark notification as read
            ApiService.acceptFriendRequest(senderId)
                .onSuccess {
                    // Force a server read update for the badge persistence
                    ApiService.markNotificationAsRead(notificationId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to accept friend request: ${error.localizedMessage}") }
                }
        }
        actionJobs.add(job) // ADDED: Track job
        job.invokeOnCompletion { actionJobs.remove(job) } // ADDED: Remove job on completion
    }

    fun deleteFriendRequest(notificationId: String) {
        val job = actionScope.launch { // CHANGED: Launch job in actionScope
            val wasUnread = _uiState.value.notifications.firstOrNull { it.id == notificationId }?.read == false

            // Optimistically update the UI: mark as read and change type to indicate dismissal
            _uiState.update { currentState ->
                currentState.copy(
                    notifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(read = true, type = "friend_deleted") else it
                    }
                )
            }
            if (wasUnread) _unreadCount.update { it - 1 }

            // Call API to delete the request (mark notification as read on server)
            ApiService.deleteFriendRequest(notificationId)
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to dismiss request: ${error.localizedMessage}") }
                }
        }
        actionJobs.add(job) // ADDED: Track job
        job.invokeOnCompletion { actionJobs.remove(job) } // ADDED: Remove job on completion
    }

    // ADDED START
    // This function blocks until all pending MarkAsRead and Accept/Delete jobs are finished.
    suspend fun awaitAllPendingActions() {
        actionJobs.joinAll()
    }
    // ADDED END
}