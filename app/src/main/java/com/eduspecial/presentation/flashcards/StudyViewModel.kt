package com.eduspecial.presentation.flashcards

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.SRSResult
import com.eduspecial.domain.usecase.RecordReviewUseCase
import com.eduspecial.utils.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class StudyUiState(
    val studyQueue: List<Flashcard> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val masteredThisSession: Int = 0,
    val reviewedThisSession: Int = 0,
    val isLoading: Boolean = false,
    val ttsEnabled: Boolean = true,
    val isSpeaking: Boolean = false
) {
    val currentCard: Flashcard? get() = studyQueue.getOrNull(currentIndex)
    val totalCards: Int get() = studyQueue.size
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel @Inject constructor(
    private val repository: FlashcardRepository,
    private val recordReviewUseCase: RecordReviewUseCase,
    val ttsManager: TtsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        observeGroups()
        loadStudyQueue()
        viewModelScope.launch {
            ttsManager.state.collect { ttsState ->
                _uiState.update { it.copy(isSpeaking = ttsState == TtsManager.TtsState.SPEAKING) }
            }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            repository.getGroupNames().collect { groups ->
                _uiState.update { current ->
                    val nextSelection = current.selectedGroup?.takeIf { it in groups }
                        ?: groups.singleOrNull()
                    current.copy(
                        availableGroups = groups,
                        selectedGroup = nextSelection
                    )
                }
                loadStudyQueue()
            }
        }
    }

    private fun loadStudyQueue() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val selectedGroup = _uiState.value.selectedGroup
            if (selectedGroup.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        studyQueue = emptyList(),
                        currentIndex = 0,
                        isFlipped = false,
                        isLoading = false
                    )
                }
                return@launch
            }
            repository.getStudyQueue(listOf(selectedGroup)).take(1).collect { queue ->
                _uiState.update {
                    it.copy(
                        studyQueue = queue,
                        currentIndex = 0,
                        isFlipped = false,
                        isLoading = false
                    )
                }
                queue.firstOrNull()?.let {
                    if (_uiState.value.ttsEnabled) speakCurrentTerm()
                }
            }
        }
    }

    fun selectGroup(groupName: String) {
        _uiState.update { current -> current.copy(selectedGroup = groupName) }
        loadStudyQueue()
    }

    fun speakCurrentTerm() {
        val card = _uiState.value.currentCard ?: return
        ttsManager.speakTerm(card.term)
    }

    fun speakCurrentDefinition() {
        val card = _uiState.value.currentCard ?: return
        ttsManager.speakFlashcardDefinition(card)
    }

    fun flipCard() {
        val wasFlipped = _uiState.value.isFlipped
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
        if (_uiState.value.ttsEnabled) {
            if (!wasFlipped) speakCurrentDefinition() else speakCurrentTerm()
        }
    }

    fun processReview(result: SRSResult) {
        val currentCard = _uiState.value.currentCard ?: return
        val isMastered = result is SRSResult.Easy
        ttsManager.stop()
        viewModelScope.launch {
            repository.processReview(currentCard, result)
            recordReviewUseCase(archivedCount = if (isMastered) 1 else 0)
        }
        _uiState.update {
            it.copy(
                currentIndex = it.currentIndex + 1,
                isFlipped = false,
                reviewedThisSession = it.reviewedThisSession + 1,
                masteredThisSession = it.masteredThisSession + if (isMastered) 1 else 0
            )
        }
        _uiState.value.currentCard?.let {
            if (_uiState.value.ttsEnabled) speakCurrentTerm()
        }
    }

    fun stopSpeaking() = ttsManager.stop()
    fun toggleTts() = _uiState.update { it.copy(ttsEnabled = !it.ttsEnabled) }
    fun restartSession() {
        _uiState.update {
            it.copy(
                currentIndex = 0,
                isFlipped = false,
                masteredThisSession = 0,
                reviewedThisSession = 0
            )
        }
        loadStudyQueue()
    }
}
