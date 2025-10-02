package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Comment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class CommentsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState

    fun fetchComments(postId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.getCommentsForPost(postId)
                .onSuccess { comments ->
                    _uiState.update { it.copy(comments = comments, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
                }
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.addComment(postId, content)
                .onSuccess {
                    // Refresh the comments list to show the new one
                    fetchComments(postId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to post comment: ${error.localizedMessage}") }
                }
        }
    }
}