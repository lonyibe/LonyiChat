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

    // --- POSTS ---
    suspend fun getPosts(): Result<List<Post>> {
        val user = Firebase.auth.currentUser
        if (user == null) {
            return Result.failure(ApiException("User not authenticated."))
        }

        return try {
            val token = user.getIdToken(true).await().token // FIX: .await() now works due to dependency fix
            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                    throw ApiException("Failed to fetch posts (${response.code}): ${getErrorMessage(errorBody)}")
                }
                val body = response.body?.string()
                val postResponse = gson.fromJson(body, PostResponse::class.java)
                Result.success(postResponse.posts)
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
            val token = user.getIdToken(true).await().token // FIX: .await() now works due to dependency fix
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                    throw ApiException("Failed to create post (${response.code}): ${getErrorMessage(errorBody)}")
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- PROFILE ---
    suspend fun getProfile(): Result<Profile> {
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val token = user.getIdToken(true).await().token // FIX: .await() now works due to dependency fix
            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .build()

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
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun updateProfile(name: String, phone: String, age: String, country: String): Result<Unit> {
        val user = Firebase.auth.currentUser ?: return Result.failure(ApiException("User not authenticated."))

        return try {
            val token = user.getIdToken(true).await().token // FIX: .await() now works due to dependency fix
            val json = gson.toJson(mapOf(
                "name" to name,
                "phone" to phone,
                "age" to age,
                "country" to country
            ))
            val body = json.toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/profile")
                .addHeader("Authorization", "Bearer $token")
                .put(body) // Use PUT for update operation
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    // 🌟 ELITE CODE MASTER FIX: Use custom exception for API errors 🌟
                    throw ApiException("Failed to update profile (${response.code}): ${getErrorMessage(errorBody)}")
                }
                Result.success(Unit)
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                val body = response.body!!.string()
                val churchResponse = gson.fromJson(body, ChurchResponse::class.java)
                Result.success(churchResponse.churches)
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                Result.success(Unit)
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                val body = response.body!!.string()
                val verseResponse = gson.fromJson(body, VerseResponse::class.java)
                Result.success(verseResponse.verse)
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("API Error: ${response.code}")
                val body = response.body!!.string()
                // Assuming the backend key is "media"
                val mediaResponse = gson.fromJson(body, MediaResponse::class.java)
                Result.success(mediaResponse.media)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}