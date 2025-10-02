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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        fetchProfile()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getProfile().onSuccess { profile ->
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            }.onFailure { error ->
                Log.e(TAG, "Error fetching profile: ${error.localizedMessage}")
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun updateProfile(
        name: String,
        phone: String,
        age: String,
        country: String,
        photoUrl: String? = null,
        onSuccess: () -> Unit // ✨ ADDED: Callback to run on successful update
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            ApiService.updateProfile(name, phone, age, country, photoUrl)
                .onSuccess {
                    val updatedProfile = _uiState.value.profile?.copy(
                        name = name,
                        phone = phone,
                        age = age.toIntOrNull() ?: _uiState.value.profile?.age,
                        country = country,
                        // Use the new photoUrl if provided, otherwise keep the existing one
                        photoUrl = photoUrl ?: _uiState.value.profile?.photoUrl
                    )
                    _uiState.update { it.copy(profile = updatedProfile, isSaving = false) }
                    onSuccess() // ✨ TRIGGER: Execute the callback
                }
                .onFailure { error ->
                    Log.e(TAG, "Error updating profile: ${error.localizedMessage}")
                    _uiState.update { it.copy(error = "Update Failed: ${error.localizedMessage}", isSaving = false) }
                }
        }
    }

    fun updateProfilePhoto(
        uri: Uri,
        context: Activity,
        onSuccess: () -> Unit // ✨ ADDED: Callback to run on successful update
    ) {
        val currentProfile = _uiState.value.profile ?: run {
            _uiState.update { it.copy(error = "Cannot update photo: Profile not loaded.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            ApiService.uploadProfilePhoto(uri, context)
                .onSuccess { newPhotoUrl ->
                    Log.d(TAG, "Backend upload successful. Updating profile.")
                    updateProfile(
                        name = currentProfile.name,
                        phone = currentProfile.phone ?: "",
                        age = currentProfile.age?.toString() ?: "",
                        country = currentProfile.country ?: "",
                        photoUrl = newPhotoUrl,
                        onSuccess = onSuccess // ✨ PASS: Pass the callback down
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "Backend upload failed: ${error.localizedMessage}")
                    _uiState.update { it.copy(error = "Photo Upload Failed: ${error.localizedMessage}", isSaving = false) }
                }
        }
    }
}