package com.eduspecial.ui.profile

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.R
import com.eduspecial.data.manager.RoleManager
import com.eduspecial.data.model.UserPreferences
import com.eduspecial.data.model.UserProfile
import com.eduspecial.data.repository.AuthRepository
import com.eduspecial.data.repository.NotificationRepository
import com.eduspecial.domain.usecase.ScheduleStudyReminderUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val roleManager: RoleManager,
    private val notificationRepository: NotificationRepository,
    private val scheduleStudyReminderUseCase: ScheduleStudyReminderUseCase
) : ViewModel() {
    companion object {
        private const val TAG = "ProfileSettingsVM"
    }

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    val uiState: StateFlow<ProfileSettingsUiState> = _uiState.asStateFlow()

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val profile = roleManager.getCurrentUserProfile()
                _uiState.value = if (profile != null) {
                    _uiState.value.copy(
                        userProfile = profile,
                        isLoading = false
                    )
                } else {
                    _uiState.value.copy(
                        error = text(R.string.profile_load_error),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.profile_load_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun updateProfile(updates: Map<String, Any>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    val success = roleManager.updateUserProfile(userId, updates)
                    if (success) {
                        loadUserProfile()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = text(R.string.profile_update_error),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = text(R.string.profile_load_error),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.profile_update_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun updatePreferences(preferences: UserPreferences) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                authRepository.updateUserPreferences(preferences).fold(
                    onSuccess = {
                        runCatching { applyPreferenceSideEffects(preferences) }
                            .onFailure { Log.w(TAG, "Skipping preference side effects", it) }
                        _uiState.value = _uiState.value.copy(
                            userProfile = _uiState.value.userProfile?.copy(preferences = preferences),
                            isLoading = false,
                            successMessage = text(R.string.preferences_saved_success)
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = text(R.string.preferences_update_error_with_reason, error.message.orEmpty()),
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.preferences_update_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                authRepository.changePassword(currentPassword, newPassword).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = text(R.string.password_change_success)
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: text(R.string.password_change_error),
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.password_change_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                authRepository.deleteAccount().fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            accountDeleted = true,
                            successMessage = text(R.string.account_delete_success)
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: text(R.string.account_delete_error),
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.account_delete_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun sendTestReminder() {
        viewModelScope.launch {
            runCatching { notificationRepository.sendTestNotification() }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        successMessage = text(R.string.test_notification_success)
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: text(R.string.test_notification_error)
                    )
                }
        }
    }

    private suspend fun applyPreferenceSideEffects(preferences: UserPreferences) {
        val reminderTimeMillis = parseReminderTimeToMillis(preferences.reminderTime)
        val remindersEnabled = preferences.notificationsEnabled && preferences.studyRemindersEnabled
        scheduleStudyReminderUseCase(remindersEnabled, reminderTimeMillis)

        runCatching {
            val currentSettings = notificationRepository.getNotificationSettings()
            notificationRepository.updateNotificationSettings(
                currentSettings.copy(
                    enabled = preferences.notificationsEnabled,
                    studyReminders = preferences.studyRemindersEnabled,
                    reminderTime = preferences.reminderTime
                )
            )
            notificationRepository.scheduleStudyReminders(
                enabled = remindersEnabled,
                time = preferences.reminderTime
            )
        }
    }

    private fun parseReminderTimeToMillis(value: String): Long {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 19
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return ((hour * 60L) + minute) * 60_000L
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}

data class ProfileSettingsUiState(
    val userProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val accountDeleted: Boolean = false
)
