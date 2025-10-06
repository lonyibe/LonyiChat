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

    private val _loadingStatusUserIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingStatusUserIds: StateFlow<Set<String>> = _loadingStatusUserIds

    // ✨ ADDED: State to track if the main search is in progress ✨
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _friendshipStatus.value = emptyMap()
            _loadingStatusUserIds.value = emptySet()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true // ✨ Set loading state to true
            try {
                val users = ApiService.searchUsers(query)
                _searchResults.value = users
                // After getting users, fetch their friendship status for each
                users.forEach { user ->
                    fetchFriendshipStatus(user.userId)
                }
            } catch (e: Exception) {
                _error.value = "Failed to search users: ${e.message}"
            } finally {
                _isSearching.value = false // ✨ Set loading state to false when done
            }
        }
    }

    private fun fetchFriendshipStatus(userId: String) {
        viewModelScope.launch {
            _loadingStatusUserIds.value = _loadingStatusUserIds.value + userId
            try {
                val result = ApiService.getFriendshipStatus(userId)
                result.onSuccess { response ->
                    _friendshipStatus.value = _friendshipStatus.value.toMutableMap().apply {
                        this[userId] = response.status
                    }
                }.onFailure { e ->
                    _error.value = "Failed to get friendship status for user $userId: ${e.message}"
                }
            } finally {
                _loadingStatusUserIds.value = _loadingStatusUserIds.value - userId
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            val result = ApiService.sendFriendRequest(userId)
            result.onSuccess {
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