package com.eduspecial.presentation.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.repository.AuthRepository
import com.eduspecial.data.repository.BookmarkRepository
import com.eduspecial.data.repository.QARepository
import com.eduspecial.domain.model.BookmarkType
import com.eduspecial.domain.model.QAAnswer
import com.eduspecial.domain.model.QAQuestion
import com.eduspecial.domain.model.DuplicateCheckResult
import com.eduspecial.domain.usecase.AcceptAnswerUseCase
import com.eduspecial.domain.usecase.EditAnswerUseCase
import com.eduspecial.domain.usecase.EditQuestionUseCase
import com.eduspecial.domain.usecase.ToggleBookmarkUseCase
import com.eduspecial.domain.usecase.UpvoteAnswerUseCase
import com.eduspecial.utils.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class QAUiState(
    val newQuestion: String = "",
    val newAnswer: String = "",
    val newHashtags: String = "",
    val isDuplicate: Boolean = false,
    val isCheckingDuplicate: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val showUnansweredOnly: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QAViewModel @Inject constructor(
    private val repository: QARepository,
    private val authRepository: AuthRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val editQuestionUseCase: EditQuestionUseCase,
    private val editAnswerUseCase: EditAnswerUseCase,
    private val acceptAnswerUseCase: AcceptAnswerUseCase,
    private val upvoteAnswerUseCase: UpvoteAnswerUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    prefs: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(QAUiState())
    val uiState: StateFlow<QAUiState> = _uiState.asStateFlow()
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _showUnanswered = MutableStateFlow(false)

    val questions: StateFlow<List<QAQuestion>> = _showUnanswered.flatMapLatest { unanswered ->
        if (unanswered) repository.getUnansweredQuestions()
        else repository.getAllQuestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The ID of the question whose answer thread is currently expanded inline. */
    private val _expandedQuestionId = MutableStateFlow<String?>(null)
    val expandedQuestionId: StateFlow<String?> = _expandedQuestionId.asStateFlow()

    /** Set of question IDs that the current user has bookmarked. */
    val bookmarkedQuestionIds: StateFlow<Set<String>> = bookmarkRepository
        .getBookmarkedQuestionIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val likedQuestionIds: StateFlow<Set<String>> = prefs.likedQaQuestionIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val likedAnswerIds: StateFlow<Set<String>> = prefs.likedQaAnswerIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** The currently authenticated user's ID — used for author-gated actions. */
    val currentUserId: String
        get() = authRepository.getCurrentUserId() ?: ""

    private var duplicateCheckJob: Job? = null

    init {
        viewModelScope.launch {
            _isAdmin.value = authRepository.isCurrentUserAdmin()
            repository.startRealtimeListeners()
            repository.refreshFromServer()
        }
    }

    fun showAll() { _showUnanswered.value = false }
    fun showUnanswered() { _showUnanswered.value = true }

    fun toggleExpanded(questionId: String) {
        _expandedQuestionId.value =
            if (_expandedQuestionId.value == questionId) null else questionId
    }

    fun focusQuestion(questionId: String) {
        _expandedQuestionId.value = questionId
    }

    fun onQuestionChange(q: String) {
        _uiState.update { it.copy(newQuestion = q, isDuplicate = false) }
        duplicateCheckJob?.cancel()
        if (q.length >= 10) {
            duplicateCheckJob = viewModelScope.launch {
                delay(600)
                _uiState.update { it.copy(isCheckingDuplicate = true) }
                val result = repository.checkDuplicate(q.trim())
                _uiState.update {
                    it.copy(
                        isDuplicate = result is DuplicateCheckResult.IsDuplicate,
                        isCheckingDuplicate = false
                    )
                }
            }
        }
    }

    fun onAnswerChange(answer: String) = _uiState.update { it.copy(newAnswer = answer) }

    fun onHashtagsChange(value: String) = _uiState.update { it.copy(newHashtags = value) }

    fun submitQuestion() {
        val state = _uiState.value
        if (state.newQuestion.isBlank()) return
        if (!authRepository.canParticipateInCommunity()) {
            _uiState.update { it.copy(error = "سجّل دخولك أولًا حتى تتمكن من نشر سؤال") }
            return
        }
        val contributorId = authRepository.getCurrentUserId()
            ?: run {
                _uiState.update { it.copy(error = "سجّل دخولك أولًا حتى تتمكن من نشر سؤال") }
                return
            }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = repository.createQuestion(
                question = state.newQuestion.trim(),
                contributorId = contributorId,
                hashtags = parseHashtags(state.newHashtags)
            )
            val isSuccess = result.isSuccess
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    newQuestion = if (isSuccess) "" else it.newQuestion,
                    newHashtags = if (isSuccess) "" else it.newHashtags
                )
            }
        }
    }

    fun submitAnswer(questionId: String, parentAnswerId: String? = null) {
        val state = _uiState.value
        if (state.newAnswer.isBlank()) return
        if (!authRepository.canParticipateInCommunity()) {
            _uiState.update { it.copy(error = "سجّل دخولك أولًا حتى تتمكن من إضافة إجابة أو رد") }
            return
        }
        val contributorId = authRepository.getCurrentUserId()
            ?: run {
                _uiState.update { it.copy(error = "سجّل دخولك أولًا حتى تتمكن من إضافة إجابة أو رد") }
                return
            }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = repository.createAnswer(
                questionId = questionId,
                content = state.newAnswer.trim(),
                contributorId = contributorId,
                parentAnswerId = parentAnswerId
            )
            val isSuccess = result.isSuccess
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    newAnswer = if (isSuccess) "" else it.newAnswer
                )
            }
        }
    }

    fun editQuestion(target: QAQuestion, question: String, hashtags: List<String>) {
        viewModelScope.launch {
            if (!authRepository.canManageContent(target.contributor)) {
                _uiState.update { it.copy(error = "لا تملك صلاحية تعديل هذا السؤال") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = editQuestionUseCase(target.id, question, hashtags)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun editAnswer(target: QAAnswer, content: String) {
        viewModelScope.launch {
            if (!authRepository.canManageContent(target.contributor)) {
                _uiState.update { it.copy(error = "لا تملك صلاحية تعديل هذه الإجابة") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = editAnswerUseCase(target.id, content)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun acceptAnswer(answerId: String, questionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = acceptAnswerUseCase(answerId, questionId)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun upvoteAnswer(answerId: String) {
        viewModelScope.launch {
            upvoteAnswerUseCase(answerId).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "فشل تحديث الإعجاب") }
            }
        }
    }

    fun upvoteQuestion(id: String) {
        viewModelScope.launch {
            repository.toggleQuestionUpvote(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "فشل تحديث الإعجاب") }
            }
        }
    }

    fun toggleBookmark(questionId: String) {
        viewModelScope.launch {
            toggleBookmarkUseCase(questionId, BookmarkType.QUESTION)
        }
    }

    fun deleteQuestion(target: QAQuestion) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (!authRepository.canManageContent(target.contributor)) {
                _uiState.update { it.copy(isLoading = false, error = "لا تملك صلاحية حذف هذا السؤال") }
                return@launch
            }
            val result = repository.deleteQuestion(target.id)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "فشل حذف السؤال") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteAnswer(target: QAAnswer) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (!authRepository.canManageContent(target.contributor)) {
                _uiState.update { it.copy(isLoading = false, error = "لا تملك صلاحية حذف هذه الإجابة") }
                return@launch
            }
            val result = repository.deleteAnswer(target.id)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "فشل حذف الإجابة") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshFromServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.refreshFromServer()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun parseHashtags(raw: String): List<String> {
        return raw.split(" ", ",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }
            .distinct()
    }
}
