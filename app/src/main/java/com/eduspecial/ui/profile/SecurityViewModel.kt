package com.eduspecial.ui.profile

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.R
import com.eduspecial.data.manager.RoleManager
import com.eduspecial.data.model.SecurityAuditLog
import com.eduspecial.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val roleManager: RoleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    fun loadSecurityInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    val profile = roleManager.getUserProfile(userId)
                    val auditLogs = roleManager.getSecurityAuditLogs(userId)

                    _uiState.value = _uiState.value.copy(
                        emailVerified = authRepository.isEmailVerified(),
                        lastLoginAt = profile?.lastLoginAt,
                        auditLogs = auditLogs,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = text(R.string.profile_load_error),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.security_load_error, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun signOutCurrentDevice() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                authRepository.signOut()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = text(R.string.sign_out_current_device_success)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = text(R.string.sign_out_error_with_reason, e.message.orEmpty()),
                    isLoading = false
                )
            }
        }
    }

    fun setupAccountRecovery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val email = authRepository.getCurrentUserEmail()
            if (email.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = text(R.string.recovery_missing_email)
                )
                return@launch
            }

            authRepository.sendPasswordReset(email).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = text(R.string.recovery_link_sent_success, email)
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: text(R.string.recovery_link_error)
                    )
                }
            )
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.sendEmailVerification().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = text(R.string.verification_link_sent_success)
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: text(R.string.verification_link_error)
                    )
                }
            )
        }
    }

    fun refreshEmailVerificationStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.reloadUser().fold(
                onSuccess = {
                    val verified = authRepository.isEmailVerified()
                    _uiState.value = _uiState.value.copy(
                        emailVerified = verified,
                        isLoading = false,
                        successMessage = if (verified) {
                            text(R.string.verification_status_verified)
                        } else {
                            text(R.string.verification_status_pending)
                        }
                    )
                    if (verified) {
                        loadSecurityInfo()
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: text(R.string.verification_status_error)
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}

data class SecurityUiState(
    val emailVerified: Boolean = false,
    val lastLoginAt: Long? = null,
    val auditLogs: List<SecurityAuditLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
