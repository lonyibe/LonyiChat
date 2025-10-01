package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Church
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChurchesUiState(
    val churches: List<Church> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChurchesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChurchesUiState())
    val uiState: StateFlow<ChurchesUiState> = _uiState

    init {
        fetchChurches()
    }

    fun fetchChurches() {
        viewModelScope.launch {
            _uiState.value = ChurchesUiState(isLoading = true)
            ApiService.getChurches().onSuccess { churches ->
                _uiState.value = ChurchesUiState(churches = churches)
            }.onFailure { error ->
                _uiState.value = ChurchesUiState(error = error.localizedMessage)
            }
        }
    }

    fun followChurch(churchId: String) {
        viewModelScope.launch {
            ApiService.followChurch(churchId).onSuccess {
                // Refresh the list to show updated follower counts, etc.
                fetchChurches()
            }.onFailure { error ->
                // Optionally, you can set an error state to show a Toast or Snackbar
                _uiState.value = _uiState.value.copy(error = "Failed to follow: ${error.localizedMessage}")
            }
        }
    }
}