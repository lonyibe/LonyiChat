package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.LonyiChatApp // Assuming this provides the application context
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.MediaItem
import com.arua.lonyichat.data.PlayerManager // IMPORTANT: Import the new PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val uploadSuccessful: Boolean = false
)

class MediaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // ✨ ADDED: Player Manager instance, initialized using application context
    private val playerManager = PlayerManager(LonyiChatApp.appContext)

    init {
        fetchMedia()
    }

    // ✨ ADDED: Expose PlayerManager for use in the composable
    fun getPlayerManager(): PlayerManager = playerManager

    // ✨ ADDED: Release all players when ViewModel is cleared to prevent memory leaks
    override fun onCleared() {
        super.onCleared()
        playerManager.releaseAllPlayers()
    }

    fun fetchMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.getMedia().onSuccess { items ->
                _uiState.update { it.copy(mediaItems = items, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun uploadMedia(uri: Uri, title: String, description: String, context: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, uploadSuccessful = false) }
            ApiService.uploadMedia(uri, title, description, context)
                .onSuccess {
                    // Refresh the media list to show the new item
                    fetchMedia()
                    _uiState.update { it.copy(isLoading = false, uploadSuccessful = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
                }
        }
    }
    fun onUploadSuccessShown() {
        _uiState.update { it.copy(uploadSuccessful = false) }
    }

    fun likeMedia(mediaId: String) {
        viewModelScope.launch {
            // 1. Optimistic Update
            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map { item ->
                        if (item.id == mediaId) {
                            val newIsLiked = !item.isLiked
                            val newLikes = item.likes + if (newIsLiked) 1 else -1
                            item.copy(isLiked = newIsLiked, likes = newLikes)
                        } else {
                            item
                        }
                    }
                )
            }

            // 2. API Call
            ApiService.likeMedia(mediaId).onFailure { error ->
                // 3. Rollback on failure (force refresh)
                fetchMedia()
                _uiState.update { it.copy(error = "Failed to like media: ${error.localizedMessage}") }
            }
        }
    }

    fun shareMedia(mediaId: String) {
        viewModelScope.launch {
            // Optimistic Update for share count
            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map { item ->
                        if (item.id == mediaId) item.copy(shareCount = item.shareCount + 1) else item
                    }
                )
            }

            ApiService.shareMedia(mediaId).onFailure { error ->
                _uiState.update { it.copy(error = "Failed to share media: ${error.localizedMessage}") }
                fetchMedia() // Revert state
            }
        }
    }

    fun downloadMedia(mediaId: String) {
        viewModelScope.launch {
            // Optimistic Update for download count
            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map { item ->
                        if (item.id == mediaId) item.copy(downloadCount = item.downloadCount + 1) else item
                    }
                )
            }

            ApiService.downloadMedia(mediaId).onFailure { error ->
                _uiState.update { it.copy(error = "Failed to download media: ${error.localizedMessage}") }
                fetchMedia() // Revert state
            }
        }
    }

    // TODO: Implement comment function that launches CommentsActivity
}