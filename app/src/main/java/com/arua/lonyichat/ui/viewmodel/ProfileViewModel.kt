package com.arua.lonyichat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
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
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    // ✨ THIS IS THE FIX: Added the init block back to fetch the current user's profile on launch ✨
    init {
        fetchCurrentUserProfile()
    }

    private fun fetchCurrentUserProfile() {
        val currentUserId = ApiService.getCurrentUserId()
        if (currentUserId != null) {
            fetchProfile(currentUserId)
        } else {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
        }
    }

    fun fetchProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            ApiService.getUserProfile(userId).onSuccess { response ->
                _uiState.update {
                    it.copy(
                        profile = response.profile,
                        posts = response.posts,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Error fetching profile for $userId: ${error.localizedMessage}")
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
        onSuccess: () -> Unit
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
                        photoUrl = photoUrl ?: _uiState.value.profile?.photoUrl
                    )
                    _uiState.update { it.copy(profile = updatedProfile, isSaving = false) }
                    onSuccess()
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
        onSuccess: () -> Unit
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
                        onSuccess = onSuccess
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "Backend upload failed: ${error.localizedMessage}")
                    _uiState.update { it.copy(error = "Photo Upload Failed: ${error.localizedMessage}", isSaving = false) }
                }
        }
    }
}