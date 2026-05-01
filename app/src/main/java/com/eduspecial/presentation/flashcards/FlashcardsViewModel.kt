package com.eduspecial.presentation.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.eduspecial.core.user.DailyCreationQuotaState
import com.eduspecial.core.user.StudyQuotaManager
import com.eduspecial.data.repository.BookmarkRepository
import com.eduspecial.data.repository.FlashcardPagingRepository
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.data.repository.AuthRepository
import com.eduspecial.domain.model.BookmarkType
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.DuplicateCheckResult
import com.eduspecial.domain.usecase.EditFlashcardUseCase
import com.eduspecial.domain.usecase.ToggleBookmarkUseCase
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.max
@Immutable
data class FlashcardsUiState(
    val newTerm: String = "",
    val newDefinition: String = "",
    val newGroupName: String = "",
    val selectedGroupName: String? = null,
    val isDuplicate: Boolean = false,
    val isCheckingDuplicate: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val undoFlashcard: Flashcard? = null,  // card pending undo-delete
    val dailyCreatedCount: Int = 0,
    val dailyCreationCap: Int = 20,
    val dailyCreationRemaining: Int = 20,
    val showCreationUnlockPrompt: Boolean = false
)

enum class FlashcardAudioUiState {
    READY,
    PREPARING_TERM,
    PREPARING_DEFINITION
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardsViewModel @Inject constructor(
    private val repository: FlashcardRepository,
    private val authRepository: AuthRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val editFlashcardUseCase: EditFlashcardUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val pagingRepository: FlashcardPagingRepository,
    val ttsManager: com.eduspecial.utils.TtsManager,
    private val studyQuotaManager: StudyQuotaManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardsUiState())
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()
    private val _audioUiStateById = MutableStateFlow<Map<String, FlashcardAudioUiState>>(emptyMap())
    val audioUiStateById: StateFlow<Map<String, FlashcardAudioUiState>> = _audioUiStateById.asStateFlow()
    private val _audioCountdownById = MutableStateFlow<Map<String, Int>>(emptyMap())
    val audioCountdownById: StateFlow<Map<String, Int>> = _audioCountdownById.asStateFlow()
    private val _selectedGroupName = MutableStateFlow<String?>(null)

    /** The currently authenticated user's ID — used for author-gated actions. */
    val currentUserId: String
        get() = authRepository.getCurrentUserId() ?: ""

    init {
        viewModelScope.launch {
            _isAdmin.value = authRepository.isCurrentUserAdmin()
        }
        viewModelScope.launch {
            repository.getAllFlashcards().collect { cards ->
                val currentUserId = authRepository.getCurrentUserId().orEmpty()
                val createdToday = if (currentUserId.isBlank()) {
                    0
                } else {
                    cards.count { card ->
                        card.contributor == currentUserId &&
                            card.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() ==
                            java.time.LocalDate.now()
                    }
                }
                _audioUiStateById.update { states ->
                    states.filterKeys { id ->
                        cards.any { card -> card.id == id }
                    }
                }
                _audioCountdownById.update { countdowns ->
                    countdowns.filterKeys { id -> cards.any { card -> card.id == id } }
                }
                applyCreationQuota(studyQuotaManager.getDailyCreationQuotaState(createdToday))
            }
        }
    }

    /**
     * Paged flashcard stream — loads [PAGE_SIZE] items at a time.
     * Backed by Room (offline-first) with RemoteMediator fetching from API.
     * cachedIn(viewModelScope) survives configuration changes.
     */
    val flashcardsPaged: Flow<PagingData<Flashcard>> = _selectedGroupName
        .flatMapLatest { groupName ->
            pagingRepository.getFlashcardsPaged(groupName)
        }
        .cachedIn(viewModelScope)

    /**
     * Non-paged flow kept for backward compatibility with existing composables
     * that haven't been migrated to LazyPagingItems yet.
     */
    val flashcards: StateFlow<List<Flashcard>> = combine(
        repository.getAllFlashcards(),
        _selectedGroupName
    ) { cards, groupName ->
        if (groupName.isNullOrBlank()) cards
        else cards.filter { it.groupName == groupName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupNames: StateFlow<List<String>> = repository.getGroupNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Set of flashcard IDs that the current user has bookmarked. */
    val bookmarkedIds: StateFlow<Set<String>> = bookmarkRepository
        .getBookmarkedFlashcardIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var duplicateCheckJob: Job? = null
    private var undoDeleteJob: Job? = null

    private fun canManageContent(ownerUserId: String): Boolean {
        return ownerUserId == currentUserId || _isAdmin.value
    }

    fun onTermChange(term: String) {
        _uiState.update { it.copy(newTerm = term, isDuplicate = false) }
        duplicateCheckJob?.cancel()
        if (term.length >= 3) {
            duplicateCheckJob = viewModelScope.launch {
                delay(500)
                _uiState.update { it.copy(isCheckingDuplicate = true) }
                val result = repository.checkDuplicate(term.trim())
                _uiState.update {
                    it.copy(
                        isDuplicate = result is DuplicateCheckResult.IsDuplicate,
                        isCheckingDuplicate = false
                    )
                }
            }
        }
    }

    fun onDefinitionChange(def: String) = _uiState.update { it.copy(newDefinition = def) }
    fun onGroupNameChange(groupName: String) = _uiState.update { it.copy(newGroupName = groupName) }

    fun filterByGroup(groupName: String?) {
        _selectedGroupName.value = groupName?.takeIf { it.isNotBlank() }
        _uiState.update { it.copy(selectedGroupName = groupName?.takeIf { it.isNotBlank() }) }
    }

    fun submitFlashcard() {
        submitFlashcardWithMedia(null, MediaType.NONE)
    }

    fun submitFlashcardWithMedia(mediaUrl: String?, mediaType: MediaType) {
        val state = _uiState.value
        if (state.newTerm.isBlank() || state.newDefinition.isBlank()) return

        val contributorId = authRepository.getCurrentUserId()
        if (contributorId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "يجب تسجيل الدخول قبل إضافة مصطلح جديد") }
            return
        }

        viewModelScope.launch {
            val quotaState = studyQuotaManager.getDailyCreationQuotaState(_uiState.value.dailyCreatedCount)
            if (quotaState.requiresUnlock) {
                applyCreationQuota(quotaState)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        showCreationUnlockPrompt = true,
                        error = null
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = repository.createFlashcard(
                term = state.newTerm.trim(),
                definition = state.newDefinition.trim(),
                groupName = state.newGroupName.trim(),
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                contributorId = contributorId
            )
            result.onSuccess { flashcard ->
                ttsManager.scheduleFlashcardDefinitionGeneration(flashcard.id)
            }
            val isSuccess = result.isSuccess
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    newTerm = if (isSuccess) "" else it.newTerm,
                    newDefinition = if (isSuccess) "" else it.newDefinition,
                    newGroupName = if (isSuccess) "" else it.newGroupName
                )
            }
        }
    }

    fun editFlashcard(
        target: Flashcard,
        term: String,
        definition: String,
        groupName: String,
        mediaUrl: String?,
        mediaType: MediaType
    ) {
        viewModelScope.launch {
            if (!canManageContent(target.contributor)) {
                _uiState.update { it.copy(error = "لا تملك صلاحية تعديل هذا المصطلح") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = editFlashcardUseCase(target.id, term, definition, groupName, mediaUrl, mediaType)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleBookmark(flashcardId: String) {
        viewModelScope.launch {
            toggleBookmarkUseCase(flashcardId, BookmarkType.FLASHCARD)
        }
    }

    /**
     * Soft-delete: removes the card locally and starts a 4-second undo window.
     * If [undoDelete] is not called within that window, the deletion is committed to the server.
     */
    fun deleteFlashcard(flashcard: Flashcard) {
        if (!canManageContent(flashcard.contributor)) {
            _uiState.update { it.copy(error = "لا تملك صلاحية حذف هذا المصطلح") }
            return
        }
        // Cancel any previous pending undo
        undoDeleteJob?.cancel()

        _uiState.update { it.copy(undoFlashcard = flashcard) }

        undoDeleteJob = viewModelScope.launch {
            delay(4_000)
            // Undo window expired — commit the deletion
            _uiState.update { it.copy(undoFlashcard = null) }
            repository.deleteFlashcard(flashcard.id).onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun deleteFlashcardImmediately(flashcard: Flashcard) {
        undoDeleteJob?.cancel()
        undoDeleteJob = null
        _uiState.update { it.copy(undoFlashcard = null, isLoading = true, error = null) }
        viewModelScope.launch {
            if (!canManageContent(flashcard.contributor)) {
                _uiState.update {
                    it.copy(isLoading = false, error = "لا تملك صلاحية حذف هذا المصطلح")
                }
                return@launch
            }
            repository.deleteFlashcard(flashcard.id).onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Call within 4 seconds of [deleteFlashcard] to cancel the deletion. */
    fun undoDelete() {
        undoDeleteJob?.cancel()
        undoDeleteJob = null
        _uiState.update { it.copy(undoFlashcard = null) }
    }

    fun dismissCreationUnlockPrompt() {
        _uiState.update { it.copy(showCreationUnlockPrompt = false) }
    }

    suspend fun unlockDailyCreationStep(): Boolean {
        val nextState = studyQuotaManager.unlockDailyCreationStep(_uiState.value.dailyCreatedCount)
        applyCreationQuota(nextState)
        _uiState.update { it.copy(showCreationUnlockPrompt = false) }
        return true
    }

    suspend fun importFromCsv(content: String): Result<Int> {
        return runCatching {
            val contributorId = authRepository.getCurrentUserId().orEmpty()
            val flashcards = parseCsv(content)
            repository.importFlashcards(flashcards, contributorId)
        }
    }

    suspend fun exportSelectedGroupCsv(groupName: String): Result<String> {
        return runCatching { repository.exportGroupToCsv(groupName) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Speak a flashcard term using TTS */
    fun speakTerm(flashcard: Flashcard) {
        if (_audioUiStateById.value[flashcard.id] == FlashcardAudioUiState.PREPARING_TERM) return
        if (!containsArabicCharacters(flashcard.term)) {
            _audioUiStateById.update { it + (flashcard.id to FlashcardAudioUiState.PREPARING_TERM) }
            viewModelScope.launch {
                launchAudioCountdown(flashcard.id, FlashcardAudioUiState.PREPARING_TERM)
                ttsManager.speakTerm(flashcard.term)
                waitForPlaybackStart(flashcard.id)
            }
            return
        }
        ttsManager.speakTerm(flashcard.term)
    }

    fun speakDefinition(flashcard: Flashcard) {
        if (_audioUiStateById.value[flashcard.id] == FlashcardAudioUiState.PREPARING_DEFINITION) return
        if (!isDefinitionAudioReady(flashcard)) {
            _audioUiStateById.update { it + (flashcard.id to FlashcardAudioUiState.PREPARING_DEFINITION) }
            viewModelScope.launch {
                launchAudioCountdown(flashcard.id, FlashcardAudioUiState.PREPARING_DEFINITION)
                ttsManager.speakFlashcardDefinition(flashcard)
                waitForPlaybackStart(flashcard.id)
            }
            return
        }
        ttsManager.speakFlashcardDefinition(flashcard)
    }

    private suspend fun waitForPlaybackStart(flashcardId: String) {
        val deadline = System.currentTimeMillis() + 20_000L
        while (System.currentTimeMillis() < deadline) {
            when (ttsManager.state.value) {
                com.eduspecial.utils.TtsManager.TtsState.SPEAKING,
                com.eduspecial.utils.TtsManager.TtsState.ERROR,
                com.eduspecial.utils.TtsManager.TtsState.UNAVAILABLE -> break
                else -> delay(250)
            }
        }
        clearAudioPreparing(flashcardId)
    }

    private fun launchAudioCountdown(flashcardId: String, targetState: FlashcardAudioUiState) {
        viewModelScope.launch {
            for (remaining in 12 downTo 1) {
                if (_audioUiStateById.value[flashcardId] != targetState) return@launch
                _audioCountdownById.update { it + (flashcardId to remaining) }
                delay(1000)
            }
            _audioCountdownById.update { it - flashcardId }
        }
    }

    private fun clearAudioPreparing(flashcardId: String) {
        _audioUiStateById.update { it - flashcardId }
        _audioCountdownById.update { it - flashcardId }
    }

    private fun isDefinitionAudioReady(flashcard: Flashcard): Boolean {
        if (containsArabicCharacters(flashcard.definition)) return true
        if (!flashcard.audioUrl.isNullOrBlank()) return true
        val localPath = flashcard.localAudioPath ?: return false
        val localFile = File(localPath)
        return localFile.exists() && localFile.length() > 0L
    }

    private fun containsArabicCharacters(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }
    }

    private fun applyCreationQuota(state: DailyCreationQuotaState) {
        _uiState.update {
            it.copy(
                dailyCreatedCount = state.createdToday,
                dailyCreationCap = state.unlockedCap,
                dailyCreationRemaining = max(state.remaining, 0)
            )
        }
    }

    private fun parseCsv(content: String): List<Flashcard> {
        val rows = content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (rows.isEmpty()) return emptyList()

        val header = parseCsvRow(rows.first()).map { it.trim().lowercase() }
        val termIndex = header.indexOfFirst { it == "term" || it == "question" }
        val definitionIndex = header.indexOfFirst { it == "definition" || it == "answer" }
        val groupIndex = header.indexOfFirst { it == "group" || it == "groupname" || it == "collection" }
        val audioUrlIndex = header.indexOfFirst { it == "audio_url" || it == "audiourl" || it == "definition_audio_url" }
        val mediaUrlIndex = header.indexOfFirst { it == "media_url" || it == "mediaurl" }
        val mediaTypeIndex = header.indexOfFirst { it == "media_type" || it == "mediatype" }

        if (termIndex == -1 || definitionIndex == -1) return emptyList()

        return rows.drop(1).mapNotNull { line ->
            val columns = parseCsvRow(line)
            val term = columns.getOrNull(termIndex)?.trim().orEmpty()
            val definition = columns.getOrNull(definitionIndex)?.trim().orEmpty()
            if (term.isBlank() || definition.isBlank()) return@mapNotNull null

            Flashcard(
                id = java.util.UUID.randomUUID().toString(),
                term = term,
                definition = definition,
                category = com.eduspecial.domain.model.FlashcardCategory.ABA_THERAPY,
                groupName = columns.getOrNull(groupIndex)?.trim().orEmpty(),
                mediaUrl = columns.getOrNull(mediaUrlIndex)?.trim()?.takeIf { it.isNotBlank() },
                mediaType = columns.getOrNull(mediaTypeIndex)
                    ?.trim()
                    ?.uppercase()
                    ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
                    ?: MediaType.NONE,
                audioUrl = columns.getOrNull(audioUrlIndex)?.trim()?.takeIf { it.isNotBlank() },
                isAudioReady = !columns.getOrNull(audioUrlIndex).isNullOrBlank(),
                contributor = currentUserId
            )
        }
    }

    private fun parseCsvRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < row.length) {
            val ch = row[index]
            when {
                ch == '"' && inQuotes && index + 1 < row.length && row[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            index++
        }
        result += current.toString()
        return result
    }
}
