package com.arua.lonyichat.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
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
    private const val BASE_URL = "https://lonyichat-backend.vercel.app" // Your Vercel URL
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Helper to extract the error message from the response body if available
    private fun getErrorMessage(responseBody: String?): String {
        return try {
            val json = gson.fromJson(responseBody, Map::class.java)
            (json["message"] as? String) ?: "Unknown API Error"
        } catch (e: Exception) {
            "Unknown Error: Invalid JSON response."
        }
    }

    // --- CLOUDINARY UPLOAD ---
    suspend fun uploadProfilePhoto(uri: Uri, context: Activity): Result<String> {
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))

        // --- CLOUDINARY CONFIGURATION (ADDED PUBLIC API KEY) ---
        // NOTE: API SECRET is NOT included, keeping the app secure.
        val CLOUD_NAME = "dncvvx6xav"
        val API_KEY = "629774465392976" // ADDED: Public API Key

        // This MUST match an Unsigned Upload Preset configured in your Cloudinary console.
        val UPLOAD_PRESET = "ml_default"
        val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
        // --- END CLOUDINARY CONFIGURATION ---

        return withContext(Dispatchers.IO) {
            try {
                // Get the file content stream
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return@withContext Result.failure(ApiException("Failed to open image file."))
                }

                // Convert InputStream to byte array for OkHttp RequestBody
                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "${user.uid}.jpg", requestBody)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .addFormDataPart("api_key", API_KEY) // FIX: Explicitly send public API Key
                    .build()

                val request = Request.Builder()
                    .url(UPLOAD_URL)
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e("Cloudinary", "Upload Failed: Code ${response.code}, Body: $responseBody")
                        val errorMsg = try {
                            val json = gson.fromJson(responseBody, Map::class.java)
                            (json["error"] as? Map<*, *>)?.get("message") as? String ?: "Cloudinary upload failed."
                        } catch (e: Exception) {
                            "Upload failed due to server error: ${response.code}"
                        }
                        return@withContext Result.failure(ApiException(errorMsg))
                    }

                    // Parse the successful Cloudinary response to get the secure URL
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                    val secureUrl = jsonResponse["secure_url"] as? String

                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Cloudinary returned a success status but no URL."))
                    }

                    Log.d("Cloudinary", "Upload successful. URL: $secureUrl")
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                Log.e("Cloudinary", "Client-side error during upload", e)
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }

    // --- POSTS ---
    suspend fun getPosts(): Result<List<Post>> {
        val user = Firebase.auth.currentUser
        if (user == null) {
            return Result.failure(ApiException("User not authenticated."))
        }

        return try {
            val token = user.getIdToken(true).await().token
            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .build()

            // FIX: Wrap blocking network call in withContext(Dispatchers.IO)
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to fetch posts (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    val body = response.body?.string()
                    val postResponse = gson.fromJson(body, PostResponse::class.java)
                    Result.success(postResponse.posts)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun createPost(content: String, type: String = "post"): Result<Unit> {
        val user = Firebase.auth.currentUser
        if (user == null) {
            return Result.failure(ApiException("User not authenticated."))
        }

        return try {
            val token = user.getIdToken(true).await().token
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
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val token = user.getIdToken(true).await().token
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
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val token = user.getIdToken(true).await().token

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
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
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
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
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

    // --- BIBLE ---
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
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
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