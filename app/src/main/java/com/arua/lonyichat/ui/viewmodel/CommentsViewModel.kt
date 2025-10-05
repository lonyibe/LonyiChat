package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Comment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class CommentsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    fun fetchComments(id: String, type: CommentType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = when (type) {
                CommentType.POST -> ApiService.getCommentsForPost(id)
                CommentType.MEDIA -> ApiService.getCommentsForMedia(id)
            }

            result.onSuccess { comments ->
                _uiState.update { it.copy(comments = comments, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun addComment(id: String, content: String, type: CommentType) {
        viewModelScope.launch {
            val result = when (type) {
                CommentType.POST -> ApiService.addComment(id, content)
                CommentType.MEDIA -> ApiService.addMediaComment(id, content)
            }

            result.onSuccess { newComment ->
                // Optimistically update the UI
                _uiState.update { currentState ->
                    currentState.copy(comments = currentState.comments + newComment)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to post comment: ${error.localizedMessage}") }
            }
        }
    }
}

enum class CommentType {
    POST, MEDIA
}