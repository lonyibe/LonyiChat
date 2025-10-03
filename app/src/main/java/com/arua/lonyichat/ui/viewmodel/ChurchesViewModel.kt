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
            // Note: joinChurch is now a toggle action on the backend
            ApiService.joinChurch(churchId).onSuccess {
                // Refresh the list to show updated membership status
                fetchChurches()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to toggle membership: ${error.localizedMessage}") }
            }
        }
    }

    // ✨ NEW: Delete Church function
    fun deleteChurch(churchId: String) {
        viewModelScope.launch {
            val originalChurches = _uiState.value.churches
            // Optimistic update: remove the church immediately
            _uiState.update { currentState ->
                currentState.copy(churches = currentState.churches.filterNot { it.id == churchId })
            }

            ApiService.deleteChurch(churchId).onFailure { error ->
                // Rollback on failure
                _uiState.update {
                    it.copy(
                        churches = originalChurches,
                        error = "Failed to delete church: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    // ✨ NEW: Toggle Member function (for an admin feature later)
    fun toggleMember(churchId: String, memberId: String, isAdding: Boolean) {
        viewModelScope.launch {
            ApiService.toggleChurchMember(churchId, memberId, isAdding).onSuccess {
                // Refresh the list to show updated member count/status
                fetchChurches()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "Failed to update member list: ${error.localizedMessage}") }
            }
        }
    }

    // NOTE: Church photo update logic will likely reside in a dedicated ChurchDetailViewModel or similar,
    // or be implemented directly in the UI for simplicity, calling the new ApiService function.
}