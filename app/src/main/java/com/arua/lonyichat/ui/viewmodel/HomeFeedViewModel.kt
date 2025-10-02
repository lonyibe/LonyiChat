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
    val isUploading: Boolean = false, // ✨ ADDED for upload indicator
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
                Log.e(TAG, "Error fetching posts: ${error.localizedMessage}", error)
                _uiState.value = HomeFeedUiState(error = error.localizedMessage)
            }
        }
    }

    fun createPost(content: String, type: String) {
        Log.d(TAG, "Attempting to create post of type: $type with content: $content")
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

    // ✨ ADDED: New function for creating posts with a photo
    fun createPhotoPost(caption: String, imageUri: Uri, activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            // Step 1: Upload the image
            ApiService.uploadPostPhoto(imageUri, activity)
                .onSuccess { imageUrl ->
                    // Step 2: Create the post with the returned image URL
                    ApiService.createPost(content = caption, imageUrl = imageUrl)
                        .onSuccess {
                            Log.d(TAG, "Photo post created successfully. Refreshing feed.")
                            fetchPosts() // Refresh the feed
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
}