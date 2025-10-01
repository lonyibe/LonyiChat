package com.arua.lonyichat.ui.viewmodel

import android.util.Log // ADDED
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeFeedViewModel" // ADDED

// Represents the state of the Home Feed screen
data class HomeFeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeFeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState

    init {
        fetchPosts()
    }

    fun fetchPosts() {
        viewModelScope.launch {
            _uiState.value = HomeFeedUiState(isLoading = true)
            val result = ApiService.getPosts()
            result.onSuccess { posts ->
                _uiState.value = HomeFeedUiState(posts = posts)
            }.onFailure { error ->
                Log.e(TAG, "Error fetching posts: ${error.localizedMessage}", error) // UPDATED LOGGING
                _uiState.value = HomeFeedUiState(error = error.localizedMessage)
            }
        }
    }

    // 🌟 UPDATED: Function to create and submit a new post with explicit logging
    fun createPost(content: String, type: String) {
        Log.d(TAG, "Attempting to create post of type: $type with content: $content") // ADDED LOGGING
        viewModelScope.launch {
            ApiService.createPost(content, type)
                .onSuccess {
                    Log.d(TAG, "Post created successfully. Refreshing feed.") // ADDED LOGGING
                    // Refresh the feed immediately after a successful post
                    fetchPosts()
                }
                .onFailure { error ->
                    val errorMessage = "Failed to create post: ${error.localizedMessage}"
                    Log.e(TAG, errorMessage, error) // UPDATED LOGGING
                    _uiState.value = _uiState.value.copy(error = errorMessage)
                }
        }
    }
}