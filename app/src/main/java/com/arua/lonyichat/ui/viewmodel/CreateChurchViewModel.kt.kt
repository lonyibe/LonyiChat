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

data class CreateChurchUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val createSuccess: Boolean = false
)

class CreateChurchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateChurchUiState())
    val uiState: StateFlow<CreateChurchUiState> = _uiState.asStateFlow()

    fun createChurch(name: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.createChurch(name, description)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, createSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                }
        }
    }

    fun resetState() {
        _uiState.value = CreateChurchUiState()
    }
}