package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.LonyiChatApp
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.MediaItem
import com.arua.lonyichat.data.PlayerManager
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

    private val playerManager = PlayerManager(LonyiChatApp.appContext)

    init {
        fetchMedia()
    }

    fun getPlayerManager(): PlayerManager = playerManager

    override fun onCleared() {
        super.onCleared()
        playerManager.releaseAllPlayers()
    }

    fun releaseAllPlayers() {
        playerManager.releaseAllPlayers()
    }

    fun pauseAllPlayers() {
        playerManager.pauseAllPlayers()
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

            ApiService.likeMedia(mediaId).onFailure { error ->
                fetchMedia()
                _uiState.update { it.copy(error = "Failed to like media: ${error.localizedMessage}") }
            }
        }
    }

    fun shareMedia(mediaId: String) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map { item ->
                        if (item.id == mediaId) item.copy(shareCount = item.shareCount + 1) else item
                    }
                )
            }

            ApiService.shareMedia(mediaId).onFailure { error ->
                _uiState.update { it.copy(error = "Failed to share media: ${error.localizedMessage}") }
                fetchMedia()
            }
        }
    }

    fun downloadMedia(context: Context, mediaItem: MediaItem) {
        viewModelScope.launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(mediaItem.url)
            val request = DownloadManager.Request(uri)
                .setTitle(mediaItem.title)
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${mediaItem.title}.mp4")

            downloadManager.enqueue(request)

            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map { item ->
                        if (item.id == mediaItem.id) item.copy(downloadCount = item.downloadCount + 1) else item
                    }
                )
            }

            ApiService.downloadMedia(mediaItem.id).onFailure { error ->
                _uiState.update { it.copy(error = "Failed to update download count: ${error.localizedMessage}") }
                fetchMedia()
            }
        }
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.deleteMedia(mediaId).onSuccess {
                _uiState.update { currentState ->
                    val updatedMediaItems = currentState.mediaItems.filter { it.id != mediaId }
                    currentState.copy(mediaItems = updatedMediaItems, isLoading = false)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to delete media: ${error.localizedMessage}", isLoading = false) }
            }
        }
    }
}