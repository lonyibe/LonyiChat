package com.arua.lonyichat.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data models to match the API response
@Serializable
data class BibleApiResponse(
    val reference: String,
    val verses: List<ApiVerse>,
    @SerialName("translation_id")
    val translationId: String,
    @SerialName("translation_name")
    val translationName: String,
)

@Serializable
data class ApiVerse(
    @SerialName("book_name")
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

// Simple data class for reading plans
data class ReadingPlan(val title: String, val progress: Float, val currentReading: String)


object BibleRepository {

    // Ktor HTTP client setup
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    // Function to fetch a chapter from the API
    suspend fun getChapter(book: String, chapter: Int, version: String): Result<BibleApiResponse> {
        return try {
            val url = "https://bible-api.com/$book $chapter?translation=$version"
            val response = client.get(url).body<BibleApiResponse>()
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Static list of books since the API doesn't provide a book list endpoint
    fun getBooks(): List<String> {
        return listOf(
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
            "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
            "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon",
            "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
            "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
            "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
            "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
            "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
            "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
        )
    }

    // Available versions from the API
    fun getVersions(): List<Pair<String, String>> {
        return listOf(
            Pair("kjv", "King James Version"),
            Pair("web", "World English Bible"),
            Pair("bbe", "Bible in Basic English")
        )
    }

    // Dummy reading plans
    fun getReadingPlans(): List<ReadingPlan> = listOf(
        ReadingPlan("Read the Old Testament", 0.1f, "Genesis 5"),
        ReadingPlan("The Life of Jesus", 0.5f, "John 3")
    )
}