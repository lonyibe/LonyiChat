package com.arua.lonyichat.data

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.arua.lonyichat.LonyiChatApp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream

class ApiException(message: String) : IOException(message)

object ApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    const val BASE_URL = "http://104.225.141.13:3000"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private var authToken: String? = null
    private var currentUserId: String? = null

    private val prefs = LonyiChatApp.appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    init {
        authToken = prefs.getString("auth_token", null)
        currentUserId = prefs.getString("user_id", null)
    }

    data class AuthResponse(val success: Boolean, val token: String, val userId: String, val message: String?)
    data class ChatConversationsResponse(val success: Boolean, val chats: List<Chat>)
    data class CommentsResponse(val success: Boolean, val comments: List<Comment>)


    private fun getErrorMessage(responseBody: String?): String {
        return try {
            val json = gson.fromJson(responseBody, Map::class.java)
            (json["message"] as? String) ?: "Unknown API Error"
        } catch (e: Exception) {
            "Unknown Error: Invalid JSON response."
        }
    }

    fun logout() {
        authToken = null
        currentUserId = null
        prefs.edit().clear().apply()
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

                    authToken = authResponse.token
                    currentUserId = authResponse.userId

                    prefs.edit()
                        .putString("auth_token", authToken)
                        .putString("user_id", currentUserId)
                        .apply()

                    Result.success(authResponse.token)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getAuthToken(): String? = authToken
    fun getCurrentUserId(): String? = currentUserId

    suspend fun uploadProfilePhoto(uri: Uri, context: Activity): Result<String> {
        val userMongoId = getCurrentUserId() ?: return Result.failure(ApiException("User not authenticated."))
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

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
                    .addFormDataPart("profileImage", "$userMongoId.jpg", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/profile")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Upload failed: ${getErrorMessage(responseBody)}")
                    }
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                    val secureUrl = jsonResponse["secure_url"] as? String
                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Backend returned a success status but no URL."))
                    }
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun uploadPostPhoto(uri: Uri, context: Activity): Result<String> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

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
                    .addFormDataPart("postImage", "post-image.jpg", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/post-image")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Upload failed: ${getErrorMessage(responseBody)}")
                    }
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                    val secureUrl = jsonResponse["secure_url"] as? String
                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Backend returned a success status but no URL."))
                    }
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun createPost(content: String, type: String = "post", imageUrl: String? = null): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val json = gson.toJson(mapOf(
                "content" to content,
                "type" to type,
                "imageUrl" to imageUrl
            ))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.e("ApiService", "POST /posts failed. Code: ${response.code}, Body: $responseBody")
                        throw ApiException("Failed to create post (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: IOException) {
            return Result.failure(ApiException("Network error: Could not connect to LonyiChat server."))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

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

    // ✨ ADDED: Functions for post interactions
    suspend fun reactToPost(postId: String, reactionType: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("reactionType" to reactionType))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/react")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("Failed to react to post")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addComment(postId: String, content: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("content" to content))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/comment")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("Failed to add comment")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sharePost(postId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val body = "".toRequestBody(JSON) // Empty body for this POST request
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/share")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("Failed to share post")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommentsForPost(postId: String): Result<List<Comment>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/comments")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to fetch comments")
                    }
                    val responseBody = response.body?.string()
                    val commentsResponse = gson.fromJson(responseBody, CommentsResponse::class.java)
                    Result.success(commentsResponse.comments)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatConversations(): Result<List<Chat>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/chats")
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e("ApiService", "GET /chats failed. Code: ${response.code}")
                        throw ApiException("Failed to fetch chats (${response.code}): ${getErrorMessage(responseBody)}")
                    }

                    val chatResponse = gson.fromJson(responseBody, ChatConversationsResponse::class.java)
                    Result.success(chatResponse.chats)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun getProfile(): Result<Profile> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
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

    suspend fun updateProfile(
        name: String,
        phone: String,
        age: String,
        country: String,
        photoUrl: String? = null
    ): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val uniquePhotoUrl = photoUrl?.let { url ->
                if (url.isNotBlank()) "${url}?t=${System.currentTimeMillis()}" else ""
            } ?: ""

            val bodyMap = mutableMapOf<String, Any>(
                "name" to name,
                "phone" to phone,
                "age" to age,
                "country" to country,
                "photoUrl" to uniquePhotoUrl
            )

            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .put(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to update profile (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun getChurches(): Result<List<Church>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches")
                .addHeader("Authorization", "Bearer $token")
                .build()

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
                .post("".toRequestBody(null))
                .build()

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

    suspend fun getVerseOfTheDay(): Result<Verse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/bible/verse-of-the-day")
                .build()

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

    suspend fun getMedia(): Result<List<MediaItem>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media")
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                    val body = response.body!!.string()
                    val mediaResponse = gson.fromJson(body, MediaResponse::class.java)
                    Result.success(mediaResponse.media)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}