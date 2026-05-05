package com.eduspecial.presentation.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.core.user.VerificationRules
import com.eduspecial.data.repository.AuthRepository
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.data.repository.LeaderboardRepository
import com.eduspecial.data.repository.NotificationRepository
import com.eduspecial.domain.model.LeaderboardPeriod
import com.eduspecial.domain.model.ReviewState
import com.eduspecial.domain.usecase.ScheduleStudyReminderUseCase
import com.eduspecial.domain.usecase.UpdateDisplayNameUseCase
import com.eduspecial.domain.usecase.UploadAvatarUseCase
import com.eduspecial.utils.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val contributionCount: Int = 0,
    val masteredCount: Int = 0,
    val reviewCount: Int = 0,
    val language: String = "ar",
    val isDarkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val dailyGoal: Int = 20,
    val isSignedOut: Boolean = false,
    val isVerified: Boolean = false,
    val avatarUrl: String? = null,
    val isUploadingAvatar: Boolean = false,
    val avatarError: String? = null,
    val isEditingName: Boolean = false,
    val nameError: String? = null,
    val isUpdatingName: Boolean = false,
    val isChangingPassword: Boolean = false,
    val passwordError: String? = null,
    val passwordSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val flashcardRepository: FlashcardRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val prefs: UserPreferencesDataStore,
    private val updateDisplayNameUseCase: UpdateDisplayNameUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val scheduleStudyReminderUseCase: ScheduleStudyReminderUseCase,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val currentUserId = authRepository.getCurrentUserId()
        val currentEmail = authRepository.getCurrentUserEmail()
        _uiState.update {
            it.copy(
                displayName = authRepository.getCurrentDisplayName() ?: "مستخدم",
                email = currentEmail ?: "",
                isVerified = VerificationRules.isOwnerAccount(currentUserId, currentEmail)
            )
        }

        viewModelScope.launch {
            flashcardRepository.getAllFlashcards()
                .map { cards ->
                    val mastered = cards.count { it.reviewState == ReviewState.ARCHIVED }
                    val toReview = cards.count {
                        it.reviewState == ReviewState.REVIEW || it.reviewState == ReviewState.LEARNING
                    }
                    mastered to toReview
                }
                .distinctUntilChanged()
                .collect { (mastered, toReview) ->
                    _uiState.update { it.copy(masteredCount = mastered, reviewCount = toReview) }
                }
        }

        viewModelScope.launch {
            combine(
                combine(prefs.isDarkTheme, prefs.dailyGoal) { dark, goal -> dark to goal },
                combine(
                    prefs.studyNotificationsEnabled,
                    prefs.avatarUrl,
                    prefs.displayName,
                    prefs.language
                ) { notifications, avatar, name, language ->
                    ProfilePrefsSnapshot(
                        isDark = false,
                        dailyGoal = 0,
                        notificationsEnabled = notifications,
                        avatarUrl = avatar,
                        displayName = name,
                        language = language
                    )
                }
            ) { (dark, goal), snapshot ->
                snapshot.copy(isDark = dark, dailyGoal = goal)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isDarkMode = snapshot.isDark,
                        dailyGoal = snapshot.dailyGoal,
                        notificationsEnabled = snapshot.notificationsEnabled,
                        avatarUrl = snapshot.avatarUrl,
                        language = snapshot.language,
                        displayName = snapshot.displayName ?: authRepository.getCurrentDisplayName() ?: "مستخدم"
                    )
                }
            }
        }

        viewModelScope.launch {
            fetchProfileFromApi()
            refreshVerificationStatus()
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingName = true, nameError = null) }
            updateDisplayNameUseCase(newName.trim()).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isEditingName = false,
                            isUpdatingName = false,
                            displayName = newName.trim()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdatingName = false,
                            nameError = error.message
                        )
                    }
                }
            )
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            Log.d(TAG, "uploadAvatar called with uri=$uri")
            _uiState.update { it.copy(isUploadingAvatar = true, avatarError = null) }
            uploadAvatarUseCase(uri).fold(
                onSuccess = { url ->
                    authRepository.syncAvatarUrlInBackground(url)
                    Log.d(TAG, "Avatar updated successfully: $url")
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarUrl = url
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Avatar upload failed", error)
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarError = error.message ?: "فشل رفع الصورة الشخصية"
                        )
                    }
                }
            )
        }
    }

    fun clearAvatarError() {
        _uiState.update { it.copy(avatarError = null) }
    }

    fun clearPasswordState() {
        _uiState.update { it.copy(passwordError = null, passwordSuccess = false) }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, passwordError = null, passwordSuccess = false) }
            authRepository.changePassword(currentPassword, newPassword).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordError = error.message
                        )
                    }
                }
            )
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setStudyNotifications(enabled)
            runCatching {
                val currentSettings = notificationRepository.getNotificationSettings()
                notificationRepository.updateNotificationSettings(
                    currentSettings.copy(studyReminders = enabled)
                )
            }
            val time = prefs.reminderTimeMillis.first()
            scheduleStudyReminderUseCase(enabled, time)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.update { it.copy(isSignedOut = true) }
    }

    fun setDailyGoal(goal: Int) = viewModelScope.launch { prefs.setDailyGoal(goal.coerceAtLeast(1)) }

    fun toggleDarkMode(enabled: Boolean) = viewModelScope.launch { prefs.setDarkTheme(enabled) }

    fun setLanguage(language: String) = viewModelScope.launch { prefs.setLanguage(language) }

    fun startEditingName() {
        _uiState.update { it.copy(isEditingName = true) }
    }

    fun cancelEditingName() {
        _uiState.update { it.copy(isEditingName = false) }
    }

    private suspend fun fetchProfileFromApi() {
        try {
            authRepository.getMyProfile()?.let { profile ->
                _uiState.update {
                    it.copy(
                        displayName = if (profile.displayName.isNotEmpty()) profile.displayName else it.displayName,
                        email = profile.email,
                        contributionCount = profile.contributionCount
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun refreshVerificationStatus() {
        val userId = authRepository.getCurrentUserId()
        val email = authRepository.getCurrentUserEmail()
        val points = leaderboardRepository.getLeaderboard(LeaderboardPeriod.ALL_TIME)
            .getOrNull()
            ?.firstOrNull { it.userId == userId }
            ?.points ?: 0

        _uiState.update {
            it.copy(
                isVerified = VerificationRules.isVerified(
                    userId = userId,
                    email = email,
                    points = points
                )
            )
        }
    }
}

private data class ProfilePrefsSnapshot(
    val isDark: Boolean,
    val dailyGoal: Int,
    val notificationsEnabled: Boolean,
    val avatarUrl: String?,
    val displayName: String?,
    val language: String
)
