package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MediaUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MediaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState

    init {
        fetchMedia()
    }

    fun fetchMedia() {
        viewModelScope.launch {
            _uiState.value = MediaUiState(isLoading = true)
            ApiService.getMedia().onSuccess { items ->
                _uiState.value = MediaUiState(mediaItems = items)
            }.onFailure { error ->
                _uiState.value = MediaUiState(error = error.localizedMessage)
            }
        }
    }
}