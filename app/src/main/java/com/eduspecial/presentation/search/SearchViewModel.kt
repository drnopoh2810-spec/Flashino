package com.eduspecial.presentation.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.data.repository.QARepository
import com.eduspecial.domain.model.SearchResult
import com.eduspecial.domain.model.SearchResultType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val filterType: SearchResultType? = null,
    val error: String? = null,
    val isLocalResults: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val qaRepository: QARepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        val normalizedQuery = query.trimStart()
        _uiState.update { it.copy(query = normalizedQuery, isLoading = normalizedQuery.length >= 2) }
        searchJob?.cancel()

        if (normalizedQuery.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(normalizedQuery)
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { SearchUiState() }
    }

    fun setFilter(type: SearchResultType?) {
        val currentQuery = _uiState.value.query
        _uiState.update { it.copy(filterType = type, isLoading = currentQuery.length >= 2) }
        if (currentQuery.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(currentQuery) }
        }
    }

    private suspend fun performSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        val filterType = _uiState.value.filterType
        val localResults = performLocalSearch(normalizedQuery, filterType)

        _uiState.update {
            it.copy(
                results = localResults,
                isLoading = false,
                error = null,
                isLocalResults = true
            )
        }
    }

    private suspend fun performLocalSearch(
        query: String,
        filterType: SearchResultType?
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        if (filterType == null || filterType == SearchResultType.FLASHCARD) {
            flashcardRepository.searchLocal(query).forEach { card ->
                results.add(
                    SearchResult(
                        id = card.id,
                        type = SearchResultType.FLASHCARD,
                        title = card.term,
                        subtitle = card.definition
                    )
                )
            }
        }

        if (filterType == null || filterType == SearchResultType.QUESTION) {
            qaRepository.searchLocal(query).forEach { question ->
                results.add(
                    SearchResult(
                        id = question.id,
                        type = SearchResultType.QUESTION,
                        title = question.question,
                        subtitle = if (question.hashtags.isEmpty()) "مجتمع الأسئلة" else question.hashtags.joinToString(" ")
                    )
                )
            }
        }

        return results
    }
}
