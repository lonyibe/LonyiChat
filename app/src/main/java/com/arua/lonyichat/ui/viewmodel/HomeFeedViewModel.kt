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
    // REMOVED: val trendingPosts: List<Post> was here
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
        // REMOVED: fetchTrendingPosts() was here
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

    // REMOVED: fun fetchTrendingPosts() was here

    fun createPost(content: String, type: String, pollOptions: List<String>? = null) {
        if (content.isBlank() && type != "poll") {
            _uiState.update { it.copy(error = "Post cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }

            ApiService.createPost(content, type, pollOptions = pollOptions)
                .onSuccess { newPost ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            posts = listOf(newPost) + currentState.posts,
                            isUploading = false
                        )
                    }
                    _postCreationSuccess.value = true
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.localizedMessage, isUploading = false) }
                }
        }
    }

    fun createPhotoPost(caption: String, imageUri: Uri, activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }

            ApiService.uploadPostPhoto(imageUri, activity)
                .onSuccess { imageUrl ->
                    createPost(caption, "post", imageUrl)
                }
                .onFailure { error ->
                    handleUploadFailure(error)
                }
        }
    }

    fun createMediaItem(title: String, mediaUri: Uri, activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }

            // Assuming a simple API call for media upload which is handled elsewhere for complexity
            // The real media upload logic is complex and relies on backend routes that are not fully implemented.
            _uiState.update { it.copy(isUploading = false, error = "Media upload logic temporarily disabled for this section.") }
        }
    }

    private fun handleUploadFailure(error: Throwable) {
        Log.e(TAG, "Upload failed: ${error.localizedMessage}", error)
        _uiState.update { it.copy(error = error.localizedMessage, isUploading = false) }
    }

    fun updatePost(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.updatePost(postId, content)
                .onSuccess {
                    fetchPosts()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.localizedMessage) }
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val originalPosts = _uiState.value.posts
            _uiState.update { it.copy(posts = it.posts.filter { p -> p.id != postId }) }

            ApiService.deletePost(postId)
                .onFailure { error ->
                    _uiState.update { it.copy(posts = originalPosts, error = error.localizedMessage) }
                }
        }
    }

    fun fetchReactors(postId: String) {
        viewModelScope.launch {
            _reactorUiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getPostReactors(postId)
                .onSuccess { response ->
                    _reactorUiState.update { it.copy(isLoading = false, reactors = response.reactions) }
                }
                .onFailure { error ->
                    _reactorUiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                }
        }
    }

    fun clearReactorState() {
        _reactorUiState.value = ReactorUiState()
    }

    fun reactToPost(postId: String, reactionType: String) {
        viewModelScope.launch {
            // Optimistic update of the post state
            val currentPost = _uiState.value.posts.find { it.id == postId } ?: return@launch
            val isTogglingOff = when (reactionType) {
                "amen" -> currentPost.userReactions.amen
                "hallelujah" -> currentPost.userReactions.hallelujah
                "praiseGod" -> currentPost.userReactions.praiseGod
                "praying" -> currentPost.userReactions.praying
                else -> false
            }

            _uiState.update { currentState ->
                currentState.copy(posts = currentState.posts.map { post ->
                    if (post.id == postId) {
                        var newReactions = post.reactions
                        var newUserReactions = post.userReactions

                        if (isTogglingOff) {
                            newReactions = when (reactionType) {
                                "amen" -> newReactions.copy(amen = newReactions.amen - 1)
                                "hallelujah" -> newReactions.copy(hallelujah = newReactions.hallelujah - 1)
                                "praiseGod" -> newReactions.copy(praiseGod = newReactions.praiseGod - 1)
                                "praying" -> newReactions.copy(praying = newReactions.praying - 1)
                                else -> newReactions
                            }
                            newUserReactions = when (reactionType) {
                                "amen" -> newUserReactions.copy(amen = false)
                                "hallelujah" -> newUserReactions.copy(hallelujah = false)
                                "praiseGod" -> newUserReactions.copy(praiseGod = false)
                                "praying" -> newUserReactions.copy(praying = false)
                                else -> newUserReactions
                            }
                        } else {
                            // Toggling on: First, reset all other reactions by the user to maintain exclusivity
                            newUserReactions = newUserReactions.copy(
                                amen = false,
                                hallelujah = false,
                                praiseGod = false,
                                praying = false
                            )
                            // Compensate the counts for toggling off other reactions (if the user had one)
                            newReactions = newReactions.copy(
                                amen = if (post.userReactions.amen) newReactions.amen - 1 else newReactions.amen,
                                hallelujah = if (post.userReactions.hallelujah) newReactions.hallelujah - 1 else newReactions.hallelujah,
                                praiseGod = if (post.userReactions.praiseGod) newReactions.praiseGod - 1 else newReactions.praiseGod,
                                praying = if (post.userReactions.praying) newReactions.praying - 1 else newReactions.praying
                            )

                            // Then, set the new reaction to true and increment its count
                            newReactions = when (reactionType) {
                                "amen" -> newReactions.copy(amen = newReactions.amen + 1)
                                "hallelujah" -> newReactions.copy(hallelujah = newReactions.hallelujah + 1)
                                "praiseGod" -> newReactions.copy(praiseGod = newReactions.praiseGod + 1)
                                "praying" -> newReactions.copy(praying = newReactions.praying + 1)
                                else -> newReactions
                            }
                            newUserReactions = when (reactionType) {
                                "amen" -> newUserReactions.copy(amen = true)
                                "hallelujah" -> newUserReactions.copy(hallelujah = true)
                                "praiseGod" -> newUserReactions.copy(praiseGod = true)
                                "praying" -> newUserReactions.copy(praying = true)
                                else -> newUserReactions
                            }
                        }

                        post.copy(reactions = newReactions, userReactions = newUserReactions)
                    } else {
                        post
                    }
                })
            }

            // API call
            ApiService.reactToPost(postId, reactionType).onFailure {
                // If API call fails, force a refresh to revert the optimistic state
                fetchPosts()
                _uiState.update { it.copy(error = "Reaction failed: ${it.error}") }
            }
        }
    }

    fun voteOnPoll(postId: String, optionId: String) {
        viewModelScope.launch {
            // API call
            ApiService.voteOnPoll(postId, optionId)
                .onSuccess { updatedPoll ->
                    _uiState.update { currentState ->
                        currentState.copy(posts = currentState.posts.map { post ->
                            if (post.id == postId) {
                                // Find the old user vote status to update the reaction count map
                                val oldVotedOptionId = post.poll?.options?.find { it.votes.any { userId -> userId == ApiService.getCurrentUserId() } }?.id

                                // Update poll details and the main post object
                                post.copy(poll = updatedPoll)
                            } else {
                                post
                            }
                        })
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.localizedMessage) }
                }
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            ApiService.addComment(postId, content).onSuccess {
                fetchPosts() // Simple refresh to update comment count
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage) }
            }
        }
    }

    fun sharePost(postId: String) {
        viewModelScope.launch {
            ApiService.sharePost(postId).onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage) }
            }
            // Optimistic update of share count
            _uiState.update { currentState ->
                currentState.copy(posts = currentState.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(shareCount = post.shareCount + 1)
                    } else {
                        post
                    }
                })
            }
        }
    }
}