package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventUiState(
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val createSuccess: Boolean = false
)

class EventViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    init {
        fetchEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getEvents().onSuccess { events ->
                _uiState.update { it.copy(events = events, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun createEventWithPhoto(title: String, description: String, imageUri: Uri, date: Long, location: String, activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.uploadEventPhoto(imageUri, activity)
                .onSuccess { imageUrl ->
                    createEvent(title, description, imageUrl, date, location)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Photo upload failed: ${error.localizedMessage}", isLoading = false) }
                }
        }
    }

    private fun createEvent(title: String, description: String, imageUrl: String?, date: Long, location: String) {
        viewModelScope.launch {
            ApiService.createEvent(title, description, imageUrl, date, location)
                .onSuccess { newEvent ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            events = listOf(newEvent) + currentState.events,
                            isLoading = false,
                            createSuccess = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Event creation failed: ${error.localizedMessage}", isLoading = false) }
                }
        }
    }

    fun resetSuccessState() {
        _uiState.update { it.copy(createSuccess = false) }
    }
}