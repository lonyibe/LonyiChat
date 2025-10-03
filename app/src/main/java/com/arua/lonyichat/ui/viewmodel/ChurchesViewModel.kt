package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Church
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChurchesUiState(
    val churches: List<Church> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChurchesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChurchesUiState())
    val uiState: StateFlow<ChurchesUiState> = _uiState.asStateFlow()

    init {
        fetchChurches()
    }

    fun fetchChurches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ApiService.getChurches().onSuccess { churches ->
                _uiState.update { it.copy(churches = churches, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun joinChurch(churchId: String) {
        viewModelScope.launch {
            ApiService.joinChurch(churchId).onSuccess {
                // Refresh the list to show updated member status
                fetchChurches()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to join: ${error.localizedMessage}") }
            }
        }
    }
}