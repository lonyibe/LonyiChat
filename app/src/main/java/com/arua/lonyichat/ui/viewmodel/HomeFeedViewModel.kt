package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Poll
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
    val trendingPosts: List<Post> = emptyList(), // ✨ ADDED: For the trending feed
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null
)

// State for the Reactor List Dialog
data class ReactorUiState(
    val isLoading: Boolean = false,
    val reactors: ReactionsWithUsers = ReactionsWithUsers(),
    val error: String? = null
)

class HomeFeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState

    // Reactor Dialog State
    private val _reactorUiState = MutableStateFlow(ReactorUiState())
    val reactorUiState: StateFlow<ReactorUiState> = _reactorUiState.asStateFlow()

    private val _postCreationSuccess = MutableStateFlow(false)
    val postCreationSuccess: StateFlow<Boolean> = _postCreationSuccess.asStateFlow()

    init {
        fetchPosts()
        fetchTrendingPosts() // ✨ ADDED: Fetch trending posts on init
    }

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

    // ✨ NEW: Function to fetch trending posts ✨
    fun fetchTrendingPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.getTrendingPosts().onSuccess { posts ->
                _uiState.update { it.copy(trendingPosts = posts, isLoading = false) }
            }.onFailure { error ->
                Log.e(TAG, "Error fetching trending posts: ${error.localizedMessage}", error)
                // Don't show an error for trending posts, just fail silently
            }
        }
    }

    fun createPost(content: String, type: String, pollOptions: List<String>? = null) {
        viewModelScope.launch {
            ApiService.createPost(content, type, pollOptions = pollOptions)
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

    fun createMediaItem(title: String, mediaUri: Uri, activity: Activity) {
        Log.d(TAG, "createMediaItem called for URI: $mediaUri")
        viewModelScope.launch {
            val finalTitle = title.trim().ifBlank { "Untitled Media" }
            _uiState.update { it.copy(isUploading = true, error = null) }
            ApiService.uploadMedia(mediaUri, finalTitle, finalTitle, activity)
                .onSuccess {
                    _uiState.update { it.copy(isUploading = false) }
                }
                .onFailure { error ->
                    handleUploadFailure(error)
                }
        }
    }

    private fun handleUploadFailure(error: Throwable) {
        val userErrorMessage = error.localizedMessage ?: "Unknown error"
        Log.e(TAG, "Failed to create photo post or media: $userErrorMessage", error)
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
                            "praying" -> post.userReactions.praying
                            else -> false
                        }

                        val updatedReactions = post.reactions.copy(
                            amen = post.reactions.amen - if (post.userReactions.amen) 1 else 0,
                            hallelujah = post.reactions.hallelujah - if (post.userReactions.hallelujah) 1 else 0,
                            praiseGod = post.reactions.praiseGod - if (post.userReactions.praiseGod) 1 else 0,
                            praying = post.reactions.praying - if (post.userReactions.praying) 1 else 0
                        )

                        val finalReactions = when {
                            isSameReaction -> updatedReactions
                            else -> updatedReactions.copy(
                                amen = updatedReactions.amen + if (reactionType == "amen") 1 else 0,
                                hallelujah = updatedReactions.hallelujah + if (reactionType == "hallelujah") 1 else 0,
                                praiseGod = updatedReactions.praiseGod + if (reactionType == "praiseGod") 1 else 0,
                                praying = updatedReactions.praying + if (reactionType == "praying") 1 else 0
                            )
                        }

                        val finalUserReactions = when {
                            isSameReaction -> post.userReactions.copy(amen = false, hallelujah = false, praiseGod = false, praying = false)
                            else -> post.userReactions.copy(
                                amen = reactionType == "amen",
                                hallelujah = reactionType == "hallelujah",
                                praiseGod = reactionType == "praiseGod",
                                praying = reactionType == "praying"
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
                _uiState.update { it.copy(posts = originalPosts) }
                Log.e("HomeFeedViewModel", "Failed to react to post: ${error.localizedMessage}")
                _uiState.update { it.copy(error = "Failed to save reaction. Please try again.") }
            }
        }
    }

    // ✨ NEW: Function to vote on a poll ✨
    fun voteOnPoll(postId: String, optionId: String) {
        viewModelScope.launch {
            ApiService.voteOnPoll(postId, optionId).onSuccess { updatedPoll ->
                _uiState.update { currentState ->
                    val updatedPosts = currentState.posts.map { post ->
                        if (post.id == postId) {
                            post.copy(poll = updatedPoll)
                        } else {
                            post
                        }
                    }
                    currentState.copy(posts = updatedPosts)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to vote: ${error.localizedMessage}") }
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