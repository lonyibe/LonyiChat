package com.arua.lonyichat.data

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.arua.lonyichat.ChurchMessage
import com.arua.lonyichat.LonyiChatApp
import com.arua.lonyichat.Message
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.io.InputStream
import java.io.File

class ApiException(message: String) : IOException(message)

data class UserProfileResponse(
    val success: Boolean,
    val profile: Profile,
    val posts: List<Post>
)

data class MessagesResponse(val success: Boolean, val messages: List<Message>)
data class SendMessageResponse(val success: Boolean, val message: Message)
data class CreateChatResponse(val success: Boolean, val chatId: String)
data class SearchUsersResponse(val success: Boolean, val users: List<Profile>)
data class FriendshipStatusResponse(val success: Boolean, val status: String)
data class MessageInteractionResponse(val success: Boolean, val updatedMessage: Message)


// ✨ NEW: Sealed class to represent all possible real-time events from the WebSocket
sealed class WebSocketEvent {
    data class NewMessage(val message: Message) : WebSocketEvent()
    data class MessageUpdated(val message: Message) : WebSocketEvent()
    data class MessageDeleted(val payload: DeletedMessageData) : WebSocketEvent()
    data class NewChurchMessage(val message: ChurchMessage) : WebSocketEvent()
    data class ChurchMessageUpdated(val message: ChurchMessage) : WebSocketEvent()
    data class ChurchMessageDeleted(val payload: DeletedMessageData) : WebSocketEvent()
    object ConnectionOpened : WebSocketEvent()
    data class ConnectionFailed(val error: String) : WebSocketEvent()
    object ConnectionClosed : WebSocketEvent()
}

// ✨ NEW: Data class for the payload of a deleted message event
data class DeletedMessageData(val messageId: String)


object ApiService {
    // Keep a single OkHttpClient instance for both HTTP and WebSocket
    private val client = OkHttpClient()
    private val gson = Gson()
    const val BASE_URL = "http://104.225.141.13:3000"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private var authToken: String? = null
    private var currentUserId: String? = null

    private val prefs = LonyiChatApp.appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // ✨ NEW: WebSocket Manager instance
    val chatSocketManager = ChatSocketManager(client, gson, BASE_URL)

    init {
        authToken = prefs.getString("auth_token", null)
        currentUserId = prefs.getString("user_id", null)
    }

    data class AuthResponse(val success: Boolean, val token: String, val userId: String, val message: String?)
    data class ChatConversationsResponse(val success: Boolean, val chats: List<Chat>)
    data class CommentsResponse(val success: Boolean, val comments: List<Comment>)
    data class SingleCommentResponse(val success: Boolean, val comment: Comment)
    data class PollVoteResponse(val success: Boolean, val poll: Poll)
    data class ChurchMessageReactionResponse(val success: Boolean, val message: ChurchMessage)
    data class EventResponse(val success: Boolean, val events: List<Event>)
    data class SingleEventResponse(val success: Boolean, val event: Event)
    data class SingleChurchResponse(val success: Boolean, val church: Church)
    data class MediaInteractionResponse(val success: Boolean, val message: String)

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
        // ✨ NEW: Disconnect WebSocket on logout
        chatSocketManager.disconnect()
    }

    suspend fun signup(email: String, password: String, username: String, phone: String, age: String, country: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf(
                "email" to email,
                "password" to password,
                "name" to username,
                "phone" to phone,
                "age" to age.toIntOrNull(),
                "country" to country
            ))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/auth/register")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException(getErrorMessage(responseBody))
                }
                val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                if (authResponse.token.isNullOrBlank()) {
                    throw ApiException("Signup succeeded but did not return a token.")
                }
                authToken = authResponse.token
                currentUserId = authResponse.userId
                prefs.edit()
                    .putString("auth_token", authToken)
                    .putString("user_id", currentUserId)
                    .apply()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signupWithProfilePhoto(
        email: String,
        password: String,
        username: String,
        phone: String,
        age: String,
        country: String,
        imageUri: Uri?,
        context: Activity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val signupResult = signup(email, password, username, phone, age, country)
            if (signupResult.isFailure) {
                return@withContext signupResult
            }

            if (imageUri != null) {
                val uploadResult = uploadProfilePhoto(imageUri, context)
                if (uploadResult.isSuccess) {
                    val photoUrl = uploadResult.getOrNull()
                    updateProfile(username, phone, age, country, photoUrl)
                } else {
                    return@withContext Result.failure(uploadResult.exceptionOrNull() ?: ApiException("Profile photo upload failed."))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf(
                "email" to email,
                "password" to password
            ))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/auth/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException(getErrorMessage(responseBody))
                }
                val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                if (authResponse.token.isNullOrBlank()) {
                    throw ApiException("Login succeeded but did not return a token.")
                }
                authToken = authResponse.token
                currentUserId = authResponse.userId
                prefs.edit()
                    .putString("auth_token", authToken)
                    .putString("user_id", currentUserId)
                    .apply()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun getAuthToken(): String? = authToken
    fun getCurrentUserId(): String? = currentUserId

    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/profile/$userId")
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to fetch user profile (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    val body = response.body!!.string()
                    val profileResponse = gson.fromJson(body, UserProfileResponse::class.java)
                    Result.success(profileResponse)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }


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

    suspend fun uploadChatMedia(uri: Uri, context: Activity): Result<String> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                var mimeType = context.contentResolver.getType(uri)

                if (mimeType == null) {
                    if (uri.path?.endsWith(".3gp", ignoreCase = true) == true) {
                        mimeType = "audio/3gpp"
                        Log.d("ApiService", "Inferred MIME type for 3gp file: $mimeType")
                    }
                }

                if (inputStream == null || mimeType == null) {
                    val uriString = uri.toString()
                    return@withContext Result.failure(ApiException("Failed to open media file. URI: $uriString, InputStream: ${inputStream != null}, MimeType: $mimeType"))
                }
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "tmp"


                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, fileBytes.size)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chatMedia", "chat-media-${System.currentTimeMillis()}.$extension", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/chat-media")
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

    suspend fun createPost(content: String, type: String, imageUrl: String? = null, pollOptions: List<String>? = null): Result<Post> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val postData = mutableMapOf<String, Any?>(
                "content" to content,
                "type" to type,
                "imageUrl" to imageUrl
            )
            if (type == "poll" && pollOptions != null) {
                postData["pollOptions"] = pollOptions
            }

            val json = gson.toJson(postData)
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e("ApiService", "POST /posts failed. Code: ${response.code}, Body: $responseBody")
                        throw ApiException("Failed to create post (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    val singlePostResponse = gson.fromJson(responseBody, SinglePostResponse::class.java)
                    Result.success(singlePostResponse.post)
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

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
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

    suspend fun updatePost(postId: String, content: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("content" to content))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId")
                .addHeader("Authorization", "Bearer $token")
                .put(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to update post (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to delete post (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException(getErrorMessage(errorBody))
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voteOnPoll(postId: String, optionId: String): Result<Poll> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("optionId" to optionId))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/vote")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to vote on poll: ${getErrorMessage(errorBody)}")
                    }
                    val responseBody = response.body!!.string()
                    val pollResponse = gson.fromJson(responseBody, PollVoteResponse::class.java)
                    Result.success(pollResponse.poll)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addComment(postId: String, content: String): Result<Comment> {
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
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to add comment: ${getErrorMessage(errorBody)}")
                    }
                    val responseBody = response.body!!.string()
                    val singleCommentResponse = gson.fromJson(responseBody, SingleCommentResponse::class.java)
                    Result.success(singleCommentResponse.comment)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sharePost(postId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val body = "".toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/share")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to share post: ${getErrorMessage(errorBody)}")
                    }
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
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to fetch comments: ${getErrorMessage(errorBody)}")
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

    suspend fun createChurch(name: String, description: String): Result<Church> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("name" to name, "description" to description))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/churches")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body!!.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to create church: ${getErrorMessage(responseBody)}")
                    }
                    val singleChurchResponse = gson.fromJson(responseBody, SingleChurchResponse::class.java)
                    Result.success(singleChurchResponse.church)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinChurch(churchId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/join")
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

    suspend fun getChurchMessages(churchId: String): Result<List<ChurchMessage>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/messages")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body!!.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to fetch messages: ${getErrorMessage(responseBody)}")
                    }
                    val messagesResponse = gson.fromJson(responseBody, ChurchMessagesResponse::class.java)
                    Result.success(messagesResponse.messages)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun postChurchMessage(
        churchId: String,
        content: String,
        repliedToMessageId: String? = null,
        repliedToMessageContent: String? = null,
        type: String = "text",
        url: String? = null
    ): Result<ChurchMessage> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val bodyMap = mutableMapOf<String, Any?>(
                "content" to content,
                "type" to type,
                "url" to url
            )
            repliedToMessageId?.let { bodyMap["repliedToMessageId"] = it }
            repliedToMessageContent?.let { bodyMap["repliedToMessageContent"] = it }

            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/messages")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body!!.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to send message: ${getErrorMessage(responseBody)}")
                    }
                    val singleMessageResponse = gson.fromJson(responseBody, SingleChurchMessageResponse::class.java)
                    Result.success(singleMessageResponse.message)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun getPostReactors(postId: String): Result<ReactorsResponse> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/posts/$postId/reactors")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to fetch reactors: ${getErrorMessage(errorBody)}")
                    }
                    val body = response.body!!.string()
                    val reactorsResponse = gson.fromJson(body, ReactorsResponse::class.java)
                    Result.success(reactorsResponse)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun uploadMedia(uri: Uri, title: String, description: String, context: Activity): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val mimeType = context.contentResolver.getType(uri)
                if (inputStream == null || mimeType == null) {
                    return@withContext Result.failure(ApiException("Failed to open media file."))
                }

                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("title", title)
                    .addFormDataPart("description", description)
                    .addFormDataPart("mediaFile", "upload", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/media")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Media upload failed: ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteChurchMessage(churchId: String, messageId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/messages/$messageId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to delete message (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactToChurchMessage(churchId: String, messageId: String, reactionEmoji: String): Result<ChurchMessage> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("reactionEmoji" to reactionEmoji))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/messages/$messageId/react")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to react to message (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    val reactionResponse = gson.fromJson(responseBody, ChurchMessageReactionResponse::class.java)
                    Result.success(reactionResponse.message)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChurchPhoto(churchId: String, uri: Uri, context: Activity): Result<String> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val mimeType = context.contentResolver.getType(uri)
                if (inputStream == null || mimeType == null) {
                    return@withContext Result.failure(ApiException("Failed to open image file."))
                }

                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, fileBytes.size)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("churchImage", "church-photo.jpg", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/church-photo/$churchId")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Photo upload failed: ${getErrorMessage(responseBody)}")
                    }
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                    val secureUrl = jsonResponse["secure_url"] as? String
                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Backend returned success but no URL."))
                    }
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun deleteChurch(churchId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to delete church: ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleChurchMember(churchId: String, memberId: String, isAdding: Boolean): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        val endpoint = if (isAdding) "add-member" else "remove-member"

        return try {
            val json = gson.toJson(mapOf("memberId" to memberId))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/$endpoint")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to update membership: ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadEventPhoto(uri: Uri, context: Activity): Result<String> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))

        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val mimeType = context.contentResolver.getType(uri)
                if (inputStream == null || mimeType == null) {
                    return@withContext Result.failure(ApiException("Failed to open image file."))
                }

                val fileBytes = inputStream.use { it.readBytes() }
                val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("eventImage", "event-image.jpg", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/upload/event-image")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Event image upload failed (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                    val secureUrl = jsonResponse["secure_url"] as? String
                    if (secureUrl.isNullOrBlank()) {
                        return@withContext Result.failure(ApiException("Backend returned success but no URL."))
                    }
                    return@withContext Result.success(secureUrl)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(ApiException("File operation failed: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun createEvent(title: String, description: String, imageUrl: String?, date: Long, location: String): Result<Event> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val bodyMap = mutableMapOf<String, Any?>(
                "title" to title,
                "description" to description,
                "imageUrl" to imageUrl,
                "date" to date,
                "location" to location
            )

            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/events")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to create event (${response.code}): ${getErrorMessage(responseBody)}")
                    }
                    val singleEventResponse = gson.fromJson(responseBody, SingleEventResponse::class.java)
                    Result.success(singleEventResponse.event)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEvents(): Result<List<Event>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/events")
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException("Failed to fetch events (${response.code})")
                    val body = response.body!!.string()
                    val eventResponse = gson.fromJson(body, EventResponse::class.java)
                    Result.success(eventResponse.events)
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/events/$eventId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to delete event (${response.code}): ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(chatId: String): List<Message> {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val request = Request.Builder()
            .url("$BASE_URL/chats/$chatId/messages")
            .addHeader("Authorization", "Bearer $token")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to get messages: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, MessagesResponse::class.java).messages
            }
        }
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        type: String = "text",
        url: String? = null,
        repliedToMessageId: String? = null,
        repliedToMessageContent: String? = null
    ): Message {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val requestBodyMap = mutableMapOf<String, Any?>(
            "text" to text,
            "type" to type
        )
        url?.let { requestBodyMap["url"] = it }
        repliedToMessageId?.let { requestBodyMap["repliedToMessageId"] = it }
        repliedToMessageContent?.let { requestBodyMap["repliedToMessageContent"] = it }


        val json = gson.toJson(requestBodyMap)
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/chats/$chatId/messages")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to send message: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, SendMessageResponse::class.java).message
            }
        }
    }

    suspend fun editMessage(messageId: String, newContent: String): Message {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val json = gson.toJson(mapOf("content" to newContent))
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/messages/$messageId")
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to edit message: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, MessageInteractionResponse::class.java).updatedMessage
            }
        }
    }

    suspend fun deleteMessage(messageId: String) {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val request = Request.Builder()
            .url("$BASE_URL/messages/$messageId")
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw ApiException("Failed to delete message: ${getErrorMessage(errorBody)}")
                }
            }
        }
    }

    suspend fun reactToMessage(messageId: String, emoji: String): Message {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val json = gson.toJson(mapOf("reactionEmoji" to emoji))
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/messages/$messageId/react")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to react to message: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, MessageInteractionResponse::class.java).updatedMessage
            }
        }
    }


    suspend fun createChat(otherUserId: String): String {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val json = gson.toJson(mapOf("otherUserId" to otherUserId))
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/chats")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to create chat: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, CreateChatResponse::class.java).chatId
            }
        }
    }

    suspend fun searchUsers(query: String): List<Profile> {
        val token = getAuthToken() ?: throw ApiException("User not authenticated.")
        val request = Request.Builder()
            .url("$BASE_URL/users/search?q=$query")
            .addHeader("Authorization", "Bearer $token")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException("Failed to search users: ${getErrorMessage(responseBody)}")
                }
                gson.fromJson(responseBody, SearchUsersResponse::class.java).users
            }
        }
    }

    suspend fun getNotifications(): Result<List<Notification>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/notifications")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException("Failed to fetch notifications: ${getErrorMessage(responseBody)}")
                    }
                    val notificationResponse = gson.fromJson(responseBody, NotificationResponse::class.java)
                    Result.success(notificationResponse.notifications)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val body = "".toRequestBody(null)
            val request = Request.Builder()
                .url("$BASE_URL/notifications/$notificationId/read")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        println("Failed to mark notification as read: ${response.body?.string()}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(userId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val body = "".toRequestBody(null)
            val request = Request.Builder()
                .url("$BASE_URL/friends/add/$userId")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to send friend request: ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriendshipStatus(userId: String): Result<FriendshipStatusResponse> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/friends/status/$userId")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to get friendship status: ${getErrorMessage(errorBody)}")
                    }
                    val responseBody = response.body!!.string()
                    val friendshipStatusResponse = gson.fromJson(responseBody, FriendshipStatusResponse::class.java)
                    Result.success(friendshipStatusResponse)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(senderId: String): Result<Unit> {
        return sendFriendRequest(senderId)
    }

    suspend fun deleteFriendRequest(notificationId: String): Result<Unit> {
        return markNotificationAsRead(notificationId)
    }

    suspend fun likeMedia(mediaId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId/like")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(null))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(getErrorMessage(response.body?.string()))
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun shareMedia(mediaId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId/share")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(null))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(getErrorMessage(response.body?.string()))
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun downloadMedia(mediaId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId/download")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(null))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(getErrorMessage(response.body?.string()))
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCommentsForMedia(mediaId: String): Result<List<Comment>> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId/comments")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to fetch media comments: ${getErrorMessage(errorBody)}")
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

    suspend fun addMediaComment(mediaId: String, content: String): Result<Comment> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val json = gson.toJson(mapOf("content" to content))
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId/comment")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to add media comment: ${getErrorMessage(errorBody)}")
                    }
                    val responseBody = response.body!!.string()
                    val singleCommentResponse = gson.fromJson(responseBody, SingleCommentResponse::class.java)
                    Result.success(singleCommentResponse.comment)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMedia(mediaId: String): Result<Unit> {
        val token = getAuthToken() ?: return Result.failure(ApiException("User not authenticated."))
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/media/$mediaId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        throw ApiException("Failed to delete media: ${getErrorMessage(errorBody)}")
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


// ✨ NEW: Class dedicated to managing the WebSocket connection and events ✨
class ChatSocketManager(
    private val client: OkHttpClient,
    private val gson: Gson,
    baseUrl: String
) {
    private val wsUrl = baseUrl.replace("http", "ws")
    private var webSocket: WebSocket? = null

    fun connect(chatId: String): Flow<WebSocketEvent> = callbackFlow {
        // Create a new WebSocket listener for each connection flow
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.d("ChatSocketManager", "WebSocket connection opened.")
                // Once connected, send a message to join the specific chat room
                val joinMessage = gson.toJson(mapOf("type" to "join_chat", "chatId" to chatId))
                webSocket.send(joinMessage)
                trySend(WebSocketEvent.ConnectionOpened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("ChatSocketManager", "Received message: $text")
                try {
                    val jsonObject = JsonParser.parseString(text).asJsonObject
                    val type = jsonObject.get("type").asString
                    val data = jsonObject.get("data")

                    val event = when (type) {
                        "new_message" -> WebSocketEvent.NewMessage(gson.fromJson(data, Message::class.java))
                        "message_updated" -> WebSocketEvent.MessageUpdated(gson.fromJson(data, Message::class.java))
                        "message_deleted" -> WebSocketEvent.MessageDeleted(gson.fromJson(data, DeletedMessageData::class.java))
                        "new_church_message" -> WebSocketEvent.NewChurchMessage(gson.fromJson(data, ChurchMessage::class.java))
                        "church_message_updated" -> WebSocketEvent.ChurchMessageUpdated(gson.fromJson(data, ChurchMessage::class.java))
                        "church_message_deleted" -> WebSocketEvent.ChurchMessageDeleted(gson.fromJson(data, DeletedMessageData::class.java))
                        else -> null
                    }
                    event?.let { trySend(it) }
                } catch (e: Exception) {
                    Log.e("ChatSocketManager", "Error parsing WebSocket message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.d("ChatSocketManager", "WebSocket closing: $code / $reason")
                trySend(WebSocketEvent.ConnectionClosed)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.d("ChatSocketManager", "WebSocket closed: $code / $reason")
                trySend(WebSocketEvent.ConnectionClosed)
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e("ChatSocketManager", "WebSocket connection failure", t)
                trySend(WebSocketEvent.ConnectionFailed(t.message ?: "Unknown error"))
                channel.close()
            }
        }

        // Build the request and create the WebSocket
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, listener)

        // This will be called when the flow is cancelled
        awaitClose {
            Log.d("ChatSocketManager", "Flow is closing, disconnecting WebSocket.")
            disconnect()
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
    }
}