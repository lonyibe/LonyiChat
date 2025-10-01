package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ProfileViewModel"

data class ProfileUiState(
    val profile: Profile? = null,
    // 🌟 Fixes 'isLoading' being unresolved
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    // 🌟 Fixes 'error' being unresolved
    val error: String? = null
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        fetchProfile()
    }

    /**
     * Fetches the user's full profile data from the backend.
     * 🌟 This method resolves the typo 'YetchProfile' in MainActivity
     */
    fun fetchProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getProfile().onSuccess { profile ->
                // 🌟 Profile now resolves
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            }.onFailure { error ->
                Log.e(TAG, "Error fetching profile: ${error.localizedMessage}")
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    /**
     * Updates the user's profile data via the backend API.
     */
    fun updateProfile(name: String, phone: String, age: String, country: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            ApiService.updateProfile(name, phone, age, country)
                .onSuccess {
                    // Update successful, refetch profile to display new data
                    fetchProfile()
                    _uiState.update { it.copy(isSaving = false) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Error updating profile: ${error.localizedMessage}")
                    _uiState.update { it.copy(error = "Update Failed: ${error.localizedMessage}", isSaving = false) }
                }
        }
    }
}