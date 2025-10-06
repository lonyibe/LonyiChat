package com.arua.lonyichat.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateChurchUiState(
    val isLoading: Boolean = false,
    val createSuccess: Boolean = false,
    val error: String? = null
)

class CreateChurchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateChurchUiState())
    val uiState: StateFlow<CreateChurchUiState> = _uiState.asStateFlow()

    fun createChurch(
        name: String,
        description: String,
        imageUri: Uri?, // Added imageUri parameter
        activity: Activity // Added activity context for the upload
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Step 1: Create the church with text details
            val createResult = ApiService.createChurch(name, description)

            createResult.onSuccess { newChurch ->
                // Step 2: If an image is selected, upload it now
                if (imageUri != null) {
                    val uploadResult = ApiService.uploadChurchPhoto(newChurch.id, imageUri, activity)
                    uploadResult.onFailure { uploadError ->
                        // If upload fails, report the error but the church is already created.
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Church created, but photo upload failed: ${uploadError.localizedMessage}"
                            )
                        }
                        // We still consider the main operation a success and will proceed to finish the screen
                        _uiState.update { it.copy(createSuccess = true) }
                        return@launch
                    }
                }
                // If everything is successful
                _uiState.update { it.copy(isLoading = false, createSuccess = true) }

            }.onFailure { createError ->
                // If the initial church creation fails
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = createError.localizedMessage
                    )
                }
            }
        }
    }
}