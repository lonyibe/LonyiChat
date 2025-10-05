package com.arua.lonyichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arua.lonyichat.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BibleUiState(
    val verseOfTheDay: Verse? = null,
    val isVerseOfTheDayLoading: Boolean = false,
    val verseOfTheDayError: String? = null,

    // Bible Reader State
    val isChapterLoading: Boolean = false,
    val chapterError: String? = null,
    val books: List<String> = emptyList(),
    val availableVersions: List<Pair<String, String>> = emptyList(),
    val chapterContent: List<ApiVerse> = emptyList(),
    val selectedBook: String = "Genesis",
    val selectedChapter: Int = 1,
    val selectedVersion: String = "kjv",
    val chapterReference: String = "Genesis 1",
    val chapterCount: Int = 0,


    val readingPlans: List<ReadingPlan> = emptyList()
)

class BibleViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BibleUiState())
    val uiState: StateFlow<BibleUiState> = _uiState

    init {
        fetchVerseOfTheDay()
        loadInitialBibleData()
    }

    private fun loadInitialBibleData() {
        _uiState.value = _uiState.value.copy(
            books = BibleRepository.getBooks(),
            availableVersions = BibleRepository.getVersions(),
            readingPlans = BibleRepository.getReadingPlans(),
            chapterCount = BibleRepository.getChapterCountForBook("Genesis")
        )
        fetchChapterContent()
    }

    fun selectBook(name: String) {
        _uiState.value = _uiState.value.copy(
            selectedBook = name,
            selectedChapter = 1, // Reset to chapter 1 when a new book is selected
            chapterCount = BibleRepository.getChapterCountForBook(name)
        )
        fetchChapterContent()
    }

    fun selectChapter(chapter: Int) {
        _uiState.value = _uiState.value.copy(selectedChapter = chapter)
        fetchChapterContent()
    }

    fun selectVersion(version: String) {
        _uiState.value = _uiState.value.copy(selectedVersion = version)
        fetchChapterContent()
    }

    private fun fetchChapterContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChapterLoading = true, chapterError = null)
            val state = _uiState.value
            BibleRepository.getChapter(state.selectedBook, state.selectedChapter, state.selectedVersion)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isChapterLoading = false,
                        chapterContent = response.verses,
                        chapterReference = response.reference
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isChapterLoading = false,
                        chapterError = "Failed to load chapter. Please check your connection.",
                        chapterContent = emptyList()
                    )
                }
        }
    }

    fun fetchVerseOfTheDay() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVerseOfTheDayLoading = true)
            ApiService.getVerseOfTheDay().onSuccess { verse ->
                _uiState.value = _uiState.value.copy(verseOfTheDay = verse, isVerseOfTheDayLoading = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(verseOfTheDayError = error.localizedMessage, isVerseOfTheDayLoading = false)
            }
        }
    }
}