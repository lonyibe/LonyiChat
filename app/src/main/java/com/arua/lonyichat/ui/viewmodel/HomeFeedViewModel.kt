package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
import com.arua.lonyichat.data.ReactionsWithUsers
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

// ✨ NEW: State for the Reactor List Dialog ✨
data class ReactorUiState(
    val isLoading: Boolean = false,
    val reactors: ReactionsWithUsers = ReactionsWithUsers(),
    val error: String? = null
)

class HomeFeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState

    // ✨ NEW: Reactor Dialog State ✨
    private val _reactorUiState = MutableStateFlow(ReactorUiState())
    val reactorUiState: StateFlow<ReactorUiState> = _reactorUiState.asStateFlow()

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
                    _postCreationSuccess.value = true
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
                            _postCreationSuccess.value = true
                        }
                        .onFailure { error -> handleUploadFailure(error) }
                }
                .onFailure { error -> handleUploadFailure(error) }
        }
    }

    // ✨ UPDATED: Function to handle direct media (video/music) upload
    fun createMediaItem(title: String, mediaUri: Uri, activity: Activity) {
        Log.d(TAG, "createMediaItem called for URI: $mediaUri") // ADDED LOG
        viewModelScope.launch {
            // Trim the title, if empty use a placeholder for safe API interaction
            val finalTitle = title.trim().ifBlank { "Untitled Media" }

            _uiState.update { it.copy(isUploading = true, error = null) }

            // Pass the finalTitle as both title and description
            ApiService.uploadMedia(mediaUri, finalTitle, finalTitle, activity)
                .onSuccess {
                    // Screen does NOT close
                    _uiState.update { it.copy(isUploading = false) }
                }
                .onFailure { error ->
                    handleUploadFailure(error)
                }
        }
    }

    private fun handleUploadFailure(error: Throwable) {
        val userErrorMessage = error.localizedMessage ?: "Unknown error"
        Log.e(TAG, "Failed to create photo post or media: $userErrorMessage", error) // MODIFIED LOG
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

    // ✨ NEW: Function to fetch reactors for the dialog ✨
    fun fetchReactors(postId: String) {
        viewModelScope.launch {
            _reactorUiState.update { ReactorUiState(isLoading = true) }
            ApiService.getPostReactors(postId).onSuccess { response ->
                _reactorUiState.update { it.copy(reactors = response.reactions, isLoading = false) }
            }.onFailure { error ->
                _reactorUiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    // ✨ NEW: Function to clear reactor state after dialog dismisses ✨
    fun clearReactorState() {
        _reactorUiState.value = ReactorUiState()
    }

    fun reactToPost(postId: String, reactionType: String) {
        viewModelScope.launch {
            val originalPosts = _uiState.value.posts
            _uiState.update { currentState ->
                val updatedPosts = currentState.posts.map { post ->
                    if (post.id == postId) {

                        val isSameReaction = when (reactionType) {
                            "amen" -> post.userReactions.amen
                            "hallelujah" -> post.userReactions.hallelujah
                            "praiseGod" -> post.userReactions.praiseGod
                            else -> false
                        }

                        // 1. Calculate new reaction counts (always reset other two, then set the new/un-set the old)
                        val updatedReactions = post.reactions.copy(
                            amen = post.reactions.amen - if (post.userReactions.amen) 1 else 0,
                            hallelujah = post.reactions.hallelujah - if (post.userReactions.hallelujah) 1 else 0,
                            praiseGod = post.reactions.praiseGod - if (post.userReactions.praiseGod) 1 else 0
                        )

                        val finalReactions = when {
                            // If user clicks the SAME reaction, it's a removal (already removed in updatedReactions)
                            isSameReaction -> updatedReactions
                            // If user clicks a NEW reaction, increment the new one
                            else -> updatedReactions.copy(
                                amen = updatedReactions.amen + if (reactionType == "amen") 1 else 0,
                                hallelujah = updatedReactions.hallelujah + if (reactionType == "hallelujah") 1 else 0,
                                praiseGod = updatedReactions.praiseGod + if (reactionType == "praiseGod") 1 else 0
                            )
                        }

                        // 2. Calculate new user reaction flags
                        val finalUserReactions = when {
                            // If user clicks the SAME reaction, clear all flags (removal)
                            isSameReaction -> post.userReactions.copy(amen = false, hallelujah = false, praiseGod = false)
                            // If user clicks a NEW reaction, set only the new one to true
                            else -> post.userReactions.copy(
                                amen = reactionType == "amen",
                                hallelujah = reactionType == "hallelujah",
                                praiseGod = reactionType == "praiseGod"
                            )
                        }

                        post.copy(reactions = finalReactions, userReactions = finalUserReactions)

                    } else {
                        post
                    }
                }
                currentState.copy(posts = updatedPosts)
            }

            ApiService.reactToPost(postId, reactionType).onFailure { error ->
                // If API fails, revert the optimistic update
                _uiState.update { it.copy(posts = originalPosts) }
                // Log the error to see what's going wrong
                Log.e("HomeFeedViewModel", "Failed to react to post: ${error.localizedMessage}")
                // Optional: Show an error to the user
                _uiState.update { it.copy(error = "Failed to save reaction. Please try again.") }
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