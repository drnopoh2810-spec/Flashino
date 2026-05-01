package com.eduspecial.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.remote.config.RemoteConfigManager
import com.eduspecial.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class AuthCurrentUser(
    val uid: String,
    val email: String?,
    val displayName: String?
)

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isEmailVerified: Boolean = false,
    val isPasswordResetSent: Boolean = false,
    val currentUser: AuthCurrentUser? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    remoteConfigManager: RemoteConfigManager
) : ViewModel() {
    companion object {
        private const val AUTH_OPERATION_TIMEOUT_MS = 15_000L
    }
    private val resolvedGoogleClientId =
        remoteConfigManager.getGoogleWebClientId().trim()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** OAuth web client id, delivered at runtime from local configuration. */
    val webClientId: StateFlow<String> = MutableStateFlow(resolvedGoogleClientId).asStateFlow()

    /**
     * Google sign-in is enabled only when a non-placeholder OAuth client id is present.
     * This avoids exposing a broken button when config still has template values.
     */
    val isGoogleSignInEnabled: StateFlow<Boolean> =
        MutableStateFlow(isValidGoogleClientId(resolvedGoogleClientId)).asStateFlow()

    init {
        if (authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    isEmailVerified = authRepository.isEmailVerified(),
                    currentUser = AuthCurrentUser(
                        uid = authRepository.getCurrentUserId().orEmpty(),
                        email = authRepository.getCurrentUserEmail(),
                        displayName = authRepository.getCurrentDisplayName()
                    )
                )
            }
        }
    }

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = null, error = null) }

    fun onPasswordChange(pw: String) =
        _uiState.update { it.copy(password = pw, passwordError = null, error = null) }

    fun onDisplayNameChange(name: String) =
        _uiState.update { it.copy(displayName = name) }

    fun switchToLogin() = _uiState.update { it.copy(isLoginMode = true, error = null) }
    fun switchToRegister() = _uiState.update { it.copy(isLoginMode = false, error = null) }

    fun login() {
        val state = _uiState.value
        if (!validate(state)) return

        launchAuthCall(
            call = { authRepository.login(state.email.trim(), state.password) },
            onSuccess = { refreshCurrentUser(authenticated = true) }
        )
    }

    fun register() {
        val state = _uiState.value
        if (!validate(state)) return

        launchAuthCall(
            call = {
                authRepository.register(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.trim().ifEmpty { state.email.substringBefore("@") }
            )
            },
            onSuccess = {
                refreshCurrentUser(authenticated = authRepository.isLoggedIn())
                if (!authRepository.isLoggedIn()) {
                    _uiState.update {
                        it.copy(error = "تم إنشاء الحساب. تحقق من بريدك الإلكتروني ثم سجّل الدخول.")
                    }
                }
            }
        )
    }

    /** Send password reset using the email currently in uiState. */
    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(emailError = "أدخل بريدك الإلكتروني أولاً") }
            return
        }
        sendPasswordReset(email)
    }

    /** Send password reset to the supplied email. */
    fun sendPasswordReset(email: String) {
        launchAuthCall(
            resetPasswordState = true,
            call = { authRepository.sendPasswordReset(email) },
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, isPasswordResetSent = true) }
            }
        )
    }

    fun clearPasswordResetState() {
        _uiState.update { it.copy(isPasswordResetSent = false, error = null) }
    }

    fun sendEmailVerification() {
        launchAuthCall(
            call = { authRepository.sendEmailVerification() },
            onSuccess = { _uiState.update { it.copy(isLoading = false) } }
        )
    }

    fun checkEmailVerification() {
        viewModelScope.launch {
            authRepository.reloadUser()
            _uiState.update {
                it.copy(
                    isEmailVerified = authRepository.isEmailVerified(),
                    currentUser = AuthCurrentUser(
                        uid = authRepository.getCurrentUserId().orEmpty(),
                        email = authRepository.getCurrentUserEmail(),
                        displayName = authRepository.getCurrentDisplayName()
                    )
                )
            }
        }
    }

    /** Complete Google sign-in with the ID token returned by GoogleSignInClient. */
    fun signInWithGoogle(idToken: String) {
        launchAuthCall(
            call = { authRepository.signInWithGoogle(idToken) },
            onSuccess = { refreshCurrentUser(authenticated = true) }
        )
    }

    /** Surface an error from the Google sign-in UI flow (cancel, no network, etc). */
    fun onGoogleSignInFailed(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun continueAsGuest() {
        launchAuthCall(
            call = { authRepository.signInAnonymously() },
            onSuccess = { refreshCurrentUser(authenticated = true) }
        )
    }

    private fun <T> launchAuthCall(
        clearError: Boolean = true,
        resetPasswordState: Boolean = false,
        call: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit = { e ->
            _uiState.update { it.copy(isLoading = false, error = mapError(e.message)) }
        }
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = if (clearError) null else it.error,
                    isPasswordResetSent = if (resetPasswordState) false else it.isPasswordResetSent
                )
            }
            try {
                withTimeout(AUTH_OPERATION_TIMEOUT_MS) {
                    call()
                        .onSuccess { value -> onSuccess(value) }
                        .onFailure { e -> onFailure(e) }
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "انتهت مهلة الاتصال. تحقق من الإنترنت ثم أعد المحاولة."
                    )
                }
            }
        }
    }

    private fun refreshCurrentUser(authenticated: Boolean) {
        _uiState.update {
            it.copy(
                isAuthenticated = authenticated,
                isLoading = false,
                isEmailVerified = authRepository.isEmailVerified(),
                currentUser = AuthCurrentUser(
                    uid = authRepository.getCurrentUserId().orEmpty(),
                    email = authRepository.getCurrentUserEmail(),
                    displayName = authRepository.getCurrentDisplayName()
                )
            )
        }
    }

    private fun validate(state: AuthUiState): Boolean {
        var valid = true
        val emailResult = com.eduspecial.utils.InputSanitizer.validateEmail(state.email)
        if (emailResult.isFailure) {
            _uiState.update { it.copy(emailError = "بريد إلكتروني غير صحيح") }
            valid = false
        }
        val passwordResult = com.eduspecial.utils.InputSanitizer.validatePassword(state.password)
        if (passwordResult.isFailure) {
            _uiState.update { it.copy(passwordError = "كلمة المرور 6 أحرف على الأقل") }
            valid = false
        }
        return valid
    }

    private fun mapError(msg: String?): String = when {
        msg == null -> "حدث خطأ غير متوقع"
        msg.contains("user-not-found") || msg.contains("wrong-password") ||
        msg.contains("INVALID_LOGIN_CREDENTIALS") -> "البريد أو كلمة المرور غير صحيحة"
        msg.contains("email-already-in-use") -> "هذا البريد مسجّل بالفعل"
        msg.contains("network") || msg.contains("Network") -> "تحقق من اتصال الإنترنت"
        msg.contains("too-many-requests") -> "محاولات كثيرة — انتظر قليلاً"
        else -> "حدث خطأ: $msg"
    }

    private fun isValidGoogleClientId(clientId: String): Boolean {
        val normalized = clientId.trim()
        if (normalized.isBlank()) return false
        if (normalized.contains("REQUIRED_", ignoreCase = true)) return false
        if (normalized.contains("REPLACE_", ignoreCase = true)) return false
        return normalized.endsWith(".apps.googleusercontent.com")
    }
}
