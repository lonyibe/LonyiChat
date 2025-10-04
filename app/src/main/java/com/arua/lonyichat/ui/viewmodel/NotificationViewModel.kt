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

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getNotifications()
                .onSuccess { notifications ->
                    _uiState.update { it.copy(isLoading = false, notifications = notifications) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            // Optimistically update the UI
            _uiState.update { currentState ->
                currentState.copy(
                    notifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(read = true) else it
                    }
                )
            }
            // Make the API call
            ApiService.markNotificationAsRead(notificationId).onFailure {
                // If the API call fails, we could optionally roll back the change,
                // but for a "read" status, it's often acceptable to let it be.
                // For a more robust solution, you'd re-fetch or revert the state.
            }
        }
    }
}