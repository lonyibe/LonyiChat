package com.arua.lonyichat.data

// Represents a single Bible verse from your API
data class Verse(
    val reference: String,
    val text: String
)

// Wrapper for the API response
data class VerseResponse(
    val success: Boolean,
    val verse: Verse
)