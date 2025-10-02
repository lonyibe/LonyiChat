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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // ✨ 1. ADDED: A state to signal when a post is successfully created
    private val _postCreationSuccess = MutableStateFlow(false)
    val postCreationSuccess: StateFlow<Boolean> = _postCreationSuccess.asStateFlow()

    init {
        fetchPosts()
    }

    // ✨ 2. ADDED: A function to reset the success signal
    fun postCreationSuccessShown() {
        _postCreationSuccess.value = false
    }

    fun fetchPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.getPosts().onSuccess { posts ->
                _uiState.update { it.copy(posts = posts, isLoading = false) }
            }.onFailure { error ->
                Log.e(TAG, "Error fetching posts: ${error.localizedMessage}", error)
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun createPost(content: String, type: String) {
        viewModelScope.launch {
            ApiService.createPost(content, type)
                .onSuccess { newPost ->
                    _uiState.update { currentState ->
                        currentState.copy(posts = listOf(newPost) + currentState.posts)
                    }
                    _postCreationSuccess.value = true // ✨ 3. TRIGGER the success signal
                }
                .onFailure { error ->
                    val userErrorMessage = error.localizedMessage ?: "Unknown connection error. Please try again."
                    Log.e(TAG, "Failed to create post: $userErrorMessage", error)
                    _uiState.update { it.copy(error = "Failed to create post: $userErrorMessage") }
                }
        }
    }

    fun createPhotoPost(caption: String, imageUri: Uri, activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            ApiService.uploadPostPhoto(imageUri, activity)
                .onSuccess { imageUrl ->
                    ApiService.createPost(content = caption, imageUrl = imageUrl)
                        .onSuccess { newPost ->
                            _uiState.update { currentState ->
                                currentState.copy(
                                    posts = listOf(newPost) + currentState.posts,
                                    isUploading = false
                                )
                            }
                            _postCreationSuccess.value = true // ✨ 4. TRIGGER the success signal
                        }
                        .onFailure { error -> handleUploadFailure(error) }
                }
                .onFailure { error -> handleUploadFailure(error) }
        }
    }

    private fun handleUploadFailure(error: Throwable) {
        val userErrorMessage = error.localizedMessage ?: "Unknown error"
        Log.e(TAG, "Failed to create photo post: $userErrorMessage", error)
        _uiState.update { it.copy(error = userErrorMessage, isUploading = false) }
    }

    fun updatePost(postId: String, content: String) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val updatedPosts = currentState.posts.map { post ->
                    if (post.id == postId) post.copy(content = content) else post
                }
                currentState.copy(posts = updatedPosts)
            }

            ApiService.updatePost(postId, content).onFailure { error ->
                fetchPosts()
                _uiState.update { it.copy(error = "Failed to update post: ${error.localizedMessage}") }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val originalPosts = _uiState.value.posts
            _uiState.update { currentState ->
                currentState.copy(posts = currentState.posts.filterNot { it.id == postId })
            }

            ApiService.deletePost(postId).onFailure { error ->
                _uiState.update {
                    it.copy(
                        posts = originalPosts,
                        error = "Failed to delete post: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    fun reactToPost(postId: String, reactionType: String) {
        viewModelScope.launch {
            val originalPosts = _uiState.value.posts
            _uiState.update { currentState ->
                val updatedPosts = currentState.posts.map { post ->
                    if (post.id == postId) {
                        val userHasReacted = when (reactionType) {
                            "amen" -> post.userReactions.amen
                            "hallelujah" -> post.userReactions.hallelujah
                            "praiseGod" -> post.userReactions.praiseGod
                            else -> false
                        }

                        val increment = if (userHasReacted) -1 else 1

                        val updatedReactions = when (reactionType) {
                            "amen" -> post.reactions.copy(amen = post.reactions.amen + increment)
                            "hallelujah" -> post.reactions.copy(hallelujah = post.reactions.hallelujah + increment)
                            "praiseGod" -> post.reactions.copy(praiseGod = post.reactions.praiseGod + increment)
                            else -> post.reactions
                        }
                        val updatedUserReactions = when (reactionType) {
                            "amen" -> post.userReactions.copy(amen = !userHasReacted)
                            "hallelujah" -> post.userReactions.copy(hallelujah = !userHasReacted)
                            "praiseGod" -> post.userReactions.copy(praiseGod = !userHasReacted)
                            else -> post.userReactions
                        }
                        post.copy(reactions = updatedReactions, userReactions = updatedUserReactions)
                    } else {
                        post
                    }
                }
                currentState.copy(posts = updatedPosts)
            }

            ApiService.reactToPost(postId, reactionType).onFailure {
                _uiState.update { it.copy(posts = originalPosts) }
            }
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.addComment(postId, content).onSuccess {
                _uiState.update { currentState ->
                    val updatedPosts = currentState.posts.map { post ->
                        if (post.id == postId) {
                            post.copy(commentCount = post.commentCount + 1)
                        } else {
                            post
                        }
                    }
                    currentState.copy(posts = updatedPosts)
                }
            }
        }
    }

    fun sharePost(postId: String) {
        viewModelScope.launch {
            ApiService.sharePost(postId).onSuccess {
                _uiState.update { currentState ->
                    val updatedPosts = currentState.posts.map { post ->
                        if (post.id == postId) {
                            post.copy(shareCount = post.shareCount + 1)
                        } else {
                            post
                        }
                    }
                    currentState.copy(posts = updatedPosts)
                }
            }
        }
    }
}