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

    fun getChapterCountForBook(bookName: String): Int {
        return when (bookName) {
            "Genesis" -> 50
            "Exodus" -> 40
            "Leviticus" -> 27
            "Numbers" -> 36
            "Deuteronomy" -> 34
            "Joshua" -> 24
            "Judges" -> 21
            "Ruth" -> 4
            "1 Samuel" -> 31
            "2 Samuel" -> 24
            "1 Kings" -> 22
            "2 Kings" -> 25
            "1 Chronicles" -> 29
            "2 Chronicles" -> 36
            "Ezra" -> 10
            "Nehemiah" -> 13
            "Esther" -> 10
            "Job" -> 42
            "Psalms" -> 150
            "Proverbs" -> 31
            "Ecclesiastes" -> 12
            "Song of Solomon" -> 8
            "Isaiah" -> 66
            "Jeremiah" -> 52
            "Lamentations" -> 5
            "Ezekiel" -> 48
            "Daniel" -> 12
            "Hosea" -> 14
            "Joel" -> 3
            "Amos" -> 9
            "Obadiah" -> 1
            "Jonah" -> 4
            "Micah" -> 7
            "Nahum" -> 3
            "Habakkuk" -> 3
            "Zephaniah" -> 3
            "Haggai" -> 2
            "Zechariah" -> 14
            "Malachi" -> 4
            "Matthew" -> 28
            "Mark" -> 16
            "Luke" -> 24
            "John" -> 21
            "Acts" -> 28
            "Romans" -> 16
            "1 Corinthians" -> 16
            "2 Corinthians" -> 13
            "Galatians" -> 6
            "Ephesians" -> 6
            "Philippians" -> 4
            "Colossians" -> 4
            "1 Thessalonians" -> 5
            "2 Thessalonians" -> 3
            "1 Timothy" -> 6
            "2 Timothy" -> 4
            "Titus" -> 3
            "Philemon" -> 1
            "Hebrews" -> 13
            "James" -> 5
            "1 Peter" -> 5
            "2 Peter" -> 3
            "1 John" -> 5
            "2 John" -> 1
            "3 John" -> 1
            "Jude" -> 1
            "Revelation" -> 22
            else -> 0
        }
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