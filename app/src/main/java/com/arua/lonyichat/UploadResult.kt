package com.arua.lonyichat.data

/**
 * A sealed class representing the state of a file upload.
 * This allows us to clearly communicate progress, success, or failure from the ApiService.
 */
sealed class UploadResult {
    data class Progress(val percentage: Int) : UploadResult()
    data class Success(val downloadUrl: String) : UploadResult()
    data class Failure(val error: Throwable) : UploadResult()
}