package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Verse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BibleUiState(
    val verseOfTheDay: Verse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class BibleViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BibleUiState())
    val uiState: StateFlow<BibleUiState> = _uiState

    init {
        fetchVerseOfTheDay()
    }

    fun fetchVerseOfTheDay() {
        viewModelScope.launch {
            _uiState.value = BibleUiState(isLoading = true)
            ApiService.getVerseOfTheDay().onSuccess { verse ->
                _uiState.value = BibleUiState(verseOfTheDay = verse)
            }.onFailure { error ->
                _uiState.value = BibleUiState(error = error.localizedMessage)
            }
        }
    }
}