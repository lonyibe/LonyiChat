package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeFeedViewModel"

data class HomeFeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            ApiService.getPosts().onSuccess { posts ->
                _uiState.value = _uiState.value.copy(posts = posts, isLoading = false)
            }.onFailure { error ->
                Log.e(TAG, "Error fetching posts: ${error.localizedMessage}", error)
                _uiState.value = _uiState.value.copy(error = error.localizedMessage, isLoading = false)
            }
        }
    }

    fun createPost(content: String, type: String) {
        viewModelScope.launch {
            ApiService.createPost(content, type)
                .onSuccess {
                    Log.d(TAG, "Post created successfully. Refreshing feed.")
                    fetchPosts()
                }
                .onFailure { error ->
                    val userErrorMessage = error.localizedMessage ?: "Unknown connection error. Please try again."
                    val errorMessage = "Failed to create post: $userErrorMessage"

                    Log.e(TAG, errorMessage, error)
                    _uiState.value = _uiState.value.copy(error = errorMessage)
                }
        }
    }

    fun createPhotoPost(caption: String, imageUri: Uri, activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            ApiService.uploadPostPhoto(imageUri, activity)
                .onSuccess { imageUrl ->
                    ApiService.createPost(content = caption, imageUrl = imageUrl)
                        .onSuccess {
                            Log.d(TAG, "Photo post created successfully. Refreshing feed.")
                            fetchPosts()
                            _uiState.value = _uiState.value.copy(isUploading = false)
                        }
                        .onFailure { error -> handleUploadFailure(error) }
                }
                .onFailure { error -> handleUploadFailure(error) }
        }
    }

    private fun handleUploadFailure(error: Throwable) {
        val userErrorMessage = error.localizedMessage ?: "Unknown error"
        Log.e(TAG, "Failed to create photo post: $userErrorMessage", error)
        _uiState.value = _uiState.value.copy(error = userErrorMessage, isUploading = false)
    }

    fun updatePost(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.updatePost(postId, content).onSuccess {
                fetchPosts() // Refresh the feed to show the updated post
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = "Failed to update post: ${error.localizedMessage}")
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            ApiService.deletePost(postId).onSuccess {
                fetchPosts() // Refresh the feed to remove the deleted post
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = "Failed to delete post: ${error.localizedMessage}")
            }
        }
    }

    // ✨ ADDED: Functions for post interactions
    fun reactToPost(postId: String, reactionType: String) {
        viewModelScope.launch {
            ApiService.reactToPost(postId, reactionType).onSuccess {
                // Optimistically update the UI before refetching
                val updatedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) {
                        val reactions = post.reactions
                        val updatedReactions = when (reactionType) {
                            "amen" -> reactions.copy(amen = reactions.amen + 1)
                            "hallelujah" -> reactions.copy(hallelujah = reactions.hallelujah + 1)
                            "praiseGod" -> reactions.copy(praiseGod = reactions.praiseGod + 1)
                            else -> reactions
                        }
                        post.copy(reactions = updatedReactions)
                    } else {
                        post
                    }
                }
                _uiState.value = _uiState.value.copy(posts = updatedPosts)

                // Refresh from server to get the definitive state
                fetchPosts()
            }
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.addComment(postId, content).onSuccess {
                fetchPosts() // Refresh to show new comment count
            }
        }
    }

    fun sharePost(postId: String) {
        viewModelScope.launch {
            ApiService.sharePost(postId).onSuccess {
                fetchPosts() // Refresh to show new share count
            }
        }
    }
}