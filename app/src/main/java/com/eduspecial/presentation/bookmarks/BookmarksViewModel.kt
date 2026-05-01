package com.eduspecial.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.QAQuestion
import com.eduspecial.domain.usecase.GetBookmarksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getBookmarks: GetBookmarksUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val bookmarksFlow = getBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val flashcardBookmarks: StateFlow<List<Flashcard>> = bookmarksFlow
        .map { it?.flashcards ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionBookmarks: StateFlow<List<QAQuestion>> = bookmarksFlow
        .map { it?.questions ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
}
