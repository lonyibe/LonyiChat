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
import android.app.Activity
import android.net.Uri

private const val TAG = "ProfileViewModel"

data class ProfileUiState(
    val profile: Profile? = null,
    // 検 Fixes 'isLoading' being unresolved
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    // 検 Fixes 'error' being unresolved
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
     */
    fun fetchProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getProfile().onSuccess { profile ->
                // 検 Profile now resolves
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
    fun updateProfile(
        name: String,
        phone: String,
        age: String,
        country: String,
        // MODIFIED: Added optional photoUrl parameter
        photoUrl: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            // MODIFIED: Pass photoUrl to ApiService
            ApiService.updateProfile(name, phone, age, country, photoUrl)
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

    // MODIFIED: New function to handle the local URI upload
    fun updateProfilePhoto(uri: Uri, context: Activity) {
        val currentProfile = _uiState.value.profile ?: run {
            _uiState.update { it.copy(error = "Cannot update photo: Profile not loaded.") }
            return
        }

        // Set isSaving state while the file upload happens
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            // Step 1: Upload the file to your backend
            ApiService.uploadProfilePhoto(uri, context)
                .onSuccess { newPhotoUrl ->
                    Log.d(TAG, "Backend upload successful. Updating profile.")
                    // Step 2: Update the user's profile with the new URL
                    updateProfile(
                        name = currentProfile.name,
                        phone = currentProfile.phone ?: "",
                        age = currentProfile.age?.toString() ?: "",
                        country = currentProfile.country ?: "",
                        photoUrl = newPhotoUrl
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "Backend upload failed: ${error.localizedMessage}")
                    _uiState.update { it.copy(error = "Photo Upload Failed: ${error.localizedMessage}", isSaving = false) }
                }
        }
    }
}