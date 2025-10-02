package com.arua.lonyichat.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Activity
import android.net.Uri
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.InputStream

// 🌟 ELITE CODE MASTER FIX: Custom exception for better error reporting 🌟
class ApiException(message: String) : IOException(message)

object ApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    // FIX: Changed from 'private const val' to 'const val' to allow access from SignupActivity.kt
    const val BASE_URL = "http://104.225.141.13:3000"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Key to store the custom JWT token locally
    private var authToken: String? = null
    // We need to store the user's MongoDB ID for local usage, as it's the primary key now
    private var currentUserId: String? = null

    // Data class for custom Auth responses
    data class AuthResponse(val success: Boolean, val token: String, val userId: String, val message: String?)

    // Helper to extract the error message from the response body if available
    private fun getErrorMessage(responseBody: String?): String {
        return try {
            val json = gson.fromJson(responseBody, Map::class.java)
            (json["message"] as? String) ?: "Unknown API Error"
        } catch (e: Exception) {
            "Unknown Error: Invalid JSON response."
        }
    }

    // -------------------------------------------------------------------------
    // 🔐 NEW AUTHENTICATION ENDPOINTS (Replaces Firebase Auth SDK) 🔐
    // -------------------------------------------------------------------------
    fun logout() {
        // Clear all local auth state
        authToken = null
        currentUserId = null
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val json = gson.toJson(mapOf(
                "email" to email,
                "password" to password
            ))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/auth/login")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)

                    if (!response.isSuccessful || authResponse.token.isNullOrBlank()) {
                        val msg = authResponse.message ?: getErrorMessage(responseBody)
                        throw ApiException("Login failed: $msg")
                    }

                    // Store the JWT token and MongoDB ID
                    authToken = authResponse.token
                    currentUserId = authResponse.userId
                    Result.success(authResponse.token)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Helper to get the JWT token and the current MongoDB ID ---
    private fun getAuthToken(): String? = authToken
    fun getCurrentUserId(): String? = currentUserId


    // -------------------------------------------------------------------------
    // 🌐 API REQUESTS (All now use the custom JWT token) 🌐
    // -------------------------------------------------------------------------

    // --- CLOUDINARY UPLOAD ---
    suspend fun uploadProfilePhoto(uri: Uri, context: Activity): Result<String> {
        val userMongoId = getCurrentUserId() ?: return Result.failure(ApiException("User not authenticated."))
        // ... (rest of Cloudinary logic remains the same, using userMongoId in filename if needed) ...
        // Using MOCK ID for file naming since we can't use Firebase UID
        val CLOUD_NAME = "dncvvx6xav"
        val API_KEY = "629774465392976"
        val UPLOAD_PRESET = "ml_default"
        val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return@withContext Result.failure(ApiException("Failed to open image file."))
                }

                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "$userMongoId.jpg", requestBody) // Use MongoDB ID
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .addFormDataPart("api_key", API_KEY)
                    .build()

                val request = Request.Builder()
                    .url(UPLOAD_URL)
                    .post(multipartBody)
                    .build()

                // ... (rest of upload logic) ...
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    // ... (error handling) ...
                    val secureUrl = gson.fromJson(responseBody, Map::class.java)["secure_url"] as? String
                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Cloudinary returned a success status but no URL."))
                    }
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }


    // --- POSTS ---
    suspend fun getPosts(): Result<List<Post>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .build()

            Log.d("ApiService", "Fetching posts from: $BASE_URL/posts")

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    Log.d("ApiService", "Response Code: ${response.code}")
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e("ApiService", "API Error Body on POSTS fail: $responseBody")
                        throw ApiException("Failed to fetch posts (${response.code}): ${getErrorMessage(responseBody)}")
                    }

                    val postResponse = gson.fromJson(responseBody, PostResponse::class.java)
                    Result.success(postResponse.posts)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // ... (All other functions need token integration)

    // Note: The rest of the functions (createPost, getProfile, updateProfile, getChurches, followChurch, getMedia)
    // should be updated to use: val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
    // as the first line, before building the request with the Authorization header.

    // To keep the example concise, I'm providing the rest of the original file content with minimal token-related modifications.

    suspend fun createPost(content: String, type: String = "post"): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val json = gson.toJson(mapOf(
                "content" to content,
                "type" to type
            ))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e("ApiService", "POST /posts failed. Code: ${response.code}, Body: $responseBody")
                        // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                        throw ApiException("Failed to create post (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: IOException) {
            // FIX: Catch low-level network errors explicitly and provide a clear message.
            return Result.failure(ApiException("Network error: Could not connect to LonyiChat server."))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- PROFILE ---
    suspend fun getProfile(): Result<Profile> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                        throw ApiException("Failed to fetch profile (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    val body = response.body!!.string()
                    val profileResponse = gson.fromJson(body, ProfileResponse::class.java)
                    Result.success(profileResponse.profile)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // MODIFIED: Added optional photoUrl parameter
    suspend fun updateProfile(
        name: String,
        phone: String,
        age: String,
        country: String,
        photoUrl: String? = null
    ): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            // FIX: Append a unique timestamp to the photoUrl before sending it to the backend.
            // This forces the backend to save a truly unique string, which resolves client-side caching.
            val uniquePhotoUrl = photoUrl?.let { url ->
                if (url.isNotBlank()) "${url}?t=${System.currentTimeMillis()}" else ""
            } ?: ""

            // MODIFIED: Build map including photoUrl
            val bodyMap = mutableMapOf<String, Any>(
                "name" to name,
                "phone" to phone,
                "age" to age,
                "country" to country,
                // Pass the new photoUrl, allowing it to be an empty string if null, which the backend handles.
                "photoUrl" to uniquePhotoUrl
            )

            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .put(body) // Use PUT for update operation
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                        throw ApiException("Failed to update profile (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- CHURCHES ---
    suspend fun getChurches(): Result<List<Church>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches")
                .addHeader("Authorization", "Bearer $token")
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                    val body = response.body!!.string()
                    val churchResponse = gson.fromJson(body, ChurchResponse::class.java)
                    Result.success(churchResponse.churches)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun followChurch(churchId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/follow")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(null)) // Empty body for this POST request
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- BIBLE (Unauthenticated) ---
    suspend fun getVerseOfTheDay(): Result<Verse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/bible/verse-of-the-day")
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                    val body = response.body!!.string()
                    val verseResponse = gson.fromJson(body, VerseResponse::class.java)
                    Result.success(verseResponse.verse)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- MEDIA ---
    suspend fun getMedia(): Result<List<MediaItem>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media")
                .addHeader("Authorization", "Bearer $token")
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                    val body = response.body!!.string()
                    // Assuming the backend key is "media"
                    val mediaResponse = gson.fromJson(body, MediaResponse::class.java)
                    Result.success(mediaResponse.media)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}