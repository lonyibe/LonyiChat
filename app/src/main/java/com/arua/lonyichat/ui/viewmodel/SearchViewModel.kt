package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserWithFriendshipStatus(
    val user: Profile,
    val friendshipStatus: String
)

data class SearchUiState(
    val searchResults: List<UserWithFriendshipStatus> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun searchUsers(query: String) {
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val users = ApiService.searchUsers(query)
                val usersWithStatus = users.map { user ->
                    val statusResult = ApiService.getFriendshipStatus(user.userId)
                    val status = statusResult.fold(
                        onSuccess = { it.status },
                        onFailure = { "none" }
                    )
                    UserWithFriendshipStatus(user, status)
                }
                _uiState.update { it.copy(searchResults = usersWithStatus, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to search users: ${e.message}", isLoading = false) }
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            val result = ApiService.sendFriendRequest(userId)
            result.onSuccess {
                // Update the friendship status of the user in the search results
                _uiState.update { currentState ->
                    val updatedResults = currentState.searchResults.map { userWithStatus ->
                        if (userWithStatus.user.userId == userId) {
                            userWithStatus.copy(friendshipStatus = "request_sent")
                        } else {
                            userWithStatus
                        }
                    }
                    currentState.copy(searchResults = updatedResults)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to send friend request: ${error.message}") }
            }
        }
    }
}