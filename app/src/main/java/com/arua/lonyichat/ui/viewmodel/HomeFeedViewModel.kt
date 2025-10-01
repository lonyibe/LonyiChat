package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
                _uiState.value = HomeFeedUiState(error = error.localizedMessage)
            }
        }
    }

    // 🌟 ADDED: Function to create and submit a new post
    fun createPost(content: String, type: String) {
        viewModelScope.launch {
            ApiService.createPost(content, type)
                .onSuccess {
                    // Refresh the feed immediately after a successful post
                    fetchPosts()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = "Failed to post: ${error.localizedMessage}")
                }
        }
    }
}