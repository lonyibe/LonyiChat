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

object ApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private const val BASE_URL = "https://lonyichat-backend.vercel.app" // Your Vercel URL
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // --- POSTS ---
    suspend fun getPosts(): Result<List<Post>> {
        val user = Firebase.auth.currentUser
        if (user == null) {
            return Result.failure(Exception("User not authenticated."))
        }

        return try {
            val token = user.getIdToken(true).await().token
            val request = Request.Builder()
                .url("$BASE_URL/posts")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code ${response.code}: ${response.body?.string()}")
                }
                val body = response.body?.string()
                val postResponse = gson.fromJson(body, PostResponse::class.java)
                Result.success(postResponse.posts)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CHURCHES ---
    suspend fun getChurches(): Result<List<Church>> {
        val user = Firebase.auth.currentUser ?: return Result.failure(Exception("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
            val request = Request.Builder()
                .url("$BASE_URL/churches")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("API Error: ${response.code}")
                val body = response.body!!.string()
                val churchResponse = gson.fromJson(body, ChurchResponse::class.java)
                Result.success(churchResponse.churches)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun followChurch(churchId: String): Result<Unit> {
        val user = Firebase.auth.currentUser ?: return Result.failure(Exception("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
            val request = Request.Builder()
                .url("$BASE_URL/churches/$churchId/follow")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(null)) // Empty body for this POST request
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("API Error: ${response.code}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- BIBLE ---
    suspend fun getVerseOfTheDay(): Result<Verse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/bible/verse-of-the-day")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("API Error: ${response.code}")
                val body = response.body!!.string()
                val verseResponse = gson.fromJson(body, VerseResponse::class.java)
                Result.success(verseResponse.verse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- MEDIA ---
    suspend fun getMedia(): Result<List<MediaItem>> {
        val user = Firebase.auth.currentUser ?: return Result.failure(Exception("User not authenticated."))
        return try {
            val token = user.getIdToken(true).await().token
            val request = Request.Builder()
                .url("$BASE_URL/media")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("API Error: ${response.code}")
                val body = response.body!!.string()
                // Assuming the backend key is "media"
                val mediaResponse = gson.fromJson(body, MediaResponse::class.java)
                Result.success(mediaResponse.media)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}