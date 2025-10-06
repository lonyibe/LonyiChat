package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Profile>>(emptyList())
    val searchResults: StateFlow<List<Profile>> = _searchResults

    private val _friendshipStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val friendshipStatus: StateFlow<Map<String, String>> = _friendshipStatus

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val users = ApiService.searchUsers(query)
                _searchResults.value = users
                // After getting users, fetch their friendship status
                users.forEach { user ->
                    fetchFriendshipStatus(user.userId)
                }
            } catch (e: Exception) {
                _error.value = "Failed to search users: ${e.message}"
            }
        }
    }

    private fun fetchFriendshipStatus(userId: String) {
        viewModelScope.launch {
            val result = ApiService.getFriendshipStatus(userId)
            result.onSuccess { response ->
                _friendshipStatus.value = _friendshipStatus.value.toMutableMap().apply {
                    this[userId] = response.status
                }
            }.onFailure { e ->
                _error.value = "Failed to get friendship status for user $userId: ${e.message}"
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            val result = ApiService.sendFriendRequest(userId)
            result.onSuccess {
                // Refresh the status after sending the request
                fetchFriendshipStatus(userId)
            }.onFailure { e ->
                _error.value = "Failed to send friend request: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}