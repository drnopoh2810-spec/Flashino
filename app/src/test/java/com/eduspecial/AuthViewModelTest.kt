package com.eduspecial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.eduspecial.data.remote.config.RemoteConfigManager
import com.eduspecial.data.repository.AuthRepository
import com.eduspecial.presentation.auth.AuthViewModel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock()
        remoteConfigManager = mock()

        whenever(authRepository.isLoggedIn()).thenReturn(false)
        whenever(authRepository.isEmailVerified()).thenReturn(false)
        whenever(authRepository.getCurrentUserId()).thenReturn(null)
        whenever(authRepository.getCurrentUserEmail()).thenReturn(null)
        whenever(authRepository.getCurrentDisplayName()).thenReturn(null)
        whenever(remoteConfigManager.getGoogleWebClientId()).thenReturn("")

        viewModel = AuthViewModel(authRepository, remoteConfigManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is login mode`() {
        viewModel.uiState.value.isLoginMode.shouldBeTrue()
    }

    @Test
    fun `initial state is not authenticated when not logged in`() {
        viewModel.uiState.value.isAuthenticated.shouldBeFalse()
    }

    @Test
    fun `initial state is authenticated when already logged in`() {
        whenever(authRepository.isLoggedIn()).thenReturn(true)
        val vm = AuthViewModel(authRepository, remoteConfigManager)
        vm.uiState.value.isAuthenticated.shouldBeTrue()
    }

    @Test
    fun `google sign in disabled when client id is missing`() {
        viewModel.isGoogleSignInEnabled.value.shouldBeFalse()
    }

    @Test
    fun `google sign in enabled when client id is valid`() {
        whenever(remoteConfigManager.getGoogleWebClientId())
            .thenReturn("demo.apps.googleusercontent.com")

        val vm = AuthViewModel(authRepository, remoteConfigManager)
        vm.isGoogleSignInEnabled.value.shouldBeTrue()
    }

    @Test
    fun `google sign in disabled when client id is placeholder`() {
        whenever(remoteConfigManager.getGoogleWebClientId())
            .thenReturn("REQUIRED_ANDROID_CLIENT_ID.apps.googleusercontent.com")

        val vm = AuthViewModel(authRepository, remoteConfigManager)
        vm.isGoogleSignInEnabled.value.shouldBeFalse()
    }

    @Test
    fun `switchToRegister changes mode`() {
        viewModel.switchToRegister()
        viewModel.uiState.value.isLoginMode.shouldBeFalse()
    }

    @Test
    fun `switchToLogin restores login mode`() {
        viewModel.switchToRegister()
        viewModel.switchToLogin()
        viewModel.uiState.value.isLoginMode.shouldBeTrue()
    }

    @Test
    fun `switchToLogin clears error`() {
        viewModel.switchToRegister()
        viewModel.switchToLogin()
        viewModel.uiState.value.error shouldBe null
    }

    @Test
    fun `onEmailChange updates email`() {
        viewModel.onEmailChange("test@example.com")
        viewModel.uiState.value.email shouldBe "test@example.com"
    }

    @Test
    fun `onEmailChange clears emailError`() {
        viewModel.login()
        viewModel.onEmailChange("new@email.com")
        viewModel.uiState.value.emailError shouldBe null
    }

    @Test
    fun `onPasswordChange updates password`() {
        viewModel.onPasswordChange("secret123")
        viewModel.uiState.value.password shouldBe "secret123"
    }

    @Test
    fun `onDisplayNameChange updates displayName`() {
        viewModel.onDisplayNameChange("Ahmed")
        viewModel.uiState.value.displayName shouldBe "Ahmed"
    }

    @Test
    fun `login with empty email sets emailError`() {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        viewModel.uiState.value.emailError.shouldNotBeNull()
    }

    @Test
    fun `login with invalid email sets emailError`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        viewModel.uiState.value.emailError.shouldNotBeNull()
    }

    @Test
    fun `login with short password sets passwordError`() {
        viewModel.onEmailChange("valid@email.com")
        viewModel.onPasswordChange("123")
        viewModel.login()
        viewModel.uiState.value.passwordError.shouldNotBeNull()
    }

    @Test
    fun `login with valid credentials calls repository`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenReturn(Result.success("uid-1"))

        viewModel.onEmailChange("valid@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        verify(authRepository).login("valid@email.com", "password123")
    }

    @Test
    fun `successful login sets isAuthenticated`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenReturn(Result.success("uid-1"))

        viewModel.onEmailChange("valid@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        viewModel.uiState.value.isAuthenticated.shouldBeTrue()
    }

    @Test
    fun `failed login sets error message`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenReturn(Result.failure(Exception("INVALID_LOGIN_CREDENTIALS")))

        viewModel.onEmailChange("valid@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        viewModel.uiState.value.error.shouldNotBeNull()
    }

    @Test
    fun `failed login does not set isAuthenticated`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenReturn(Result.failure(Exception("wrong-password")))

        viewModel.onEmailChange("valid@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        viewModel.uiState.value.isAuthenticated.shouldBeFalse()
    }

    @Test
    fun `sendPasswordReset with empty email sets emailError`() {
        viewModel.onEmailChange("")
        viewModel.sendPasswordReset()
        viewModel.uiState.value.emailError.shouldNotBeNull()
    }

    @Test
    fun `sendPasswordReset with valid email calls repository`() = runTest {
        whenever(authRepository.sendPasswordReset(any()))
            .thenReturn(Result.success(Unit))

        viewModel.onEmailChange("valid@email.com")
        viewModel.sendPasswordReset()
        advanceUntilIdle()

        verify(authRepository).sendPasswordReset("valid@email.com")
    }

    @Test
    fun `sendPasswordReset success sets password reset flag`() = runTest {
        whenever(authRepository.sendPasswordReset(any()))
            .thenReturn(Result.success(Unit))

        viewModel.onEmailChange("valid@email.com")
        viewModel.sendPasswordReset()
        advanceUntilIdle()

        viewModel.uiState.value.isPasswordResetSent.shouldBeTrue()
    }

    @Test
    fun `clearPasswordResetState clears success flag and error`() {
        viewModel.onGoogleSignInFailed("temporary error")
        viewModel.clearPasswordResetState()
        viewModel.uiState.value.isPasswordResetSent.shouldBeFalse()
        viewModel.uiState.value.error shouldBe null
    }

    @Test
    fun `register with valid data calls repository`() = runTest {
        whenever(authRepository.register(any(), any(), any()))
            .thenReturn(Result.success("uid-2"))

        viewModel.switchToRegister()
        viewModel.onDisplayNameChange("Ahmed")
        viewModel.onEmailChange("register@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.register()
        advanceUntilIdle()

        verify(authRepository).register("register@email.com", "password123", "Ahmed")
    }

    @Test
    fun `register failure sets error and keeps unauthenticated`() = runTest {
        whenever(authRepository.register(any(), any(), any()))
            .thenReturn(Result.failure(Exception("email-already-in-use")))

        viewModel.switchToRegister()
        viewModel.onDisplayNameChange("Ahmed")
        viewModel.onEmailChange("register@email.com")
        viewModel.onPasswordChange("password123")
        viewModel.register()
        advanceUntilIdle()

        viewModel.uiState.value.error.shouldNotBeNull()
        viewModel.uiState.value.isAuthenticated.shouldBeFalse()
    }

    @Test
    fun `google sign in success authenticates user`() = runTest {
        whenever(authRepository.signInWithGoogle(any()))
            .thenReturn(Result.success("uid-google"))

        viewModel.signInWithGoogle("token123")
        advanceUntilIdle()

        viewModel.uiState.value.isAuthenticated.shouldBeTrue()
    }

    @Test
    fun `google sign in failure maps error into ui state`() = runTest {
        whenever(authRepository.signInWithGoogle(any()))
            .thenReturn(Result.failure(Exception("network down")))

        viewModel.signInWithGoogle("token123")
        advanceUntilIdle()

        viewModel.uiState.value.error.shouldNotBeNull()
    }

    @Test
    fun `onGoogleSignInFailed surfaces message`() {
        viewModel.onGoogleSignInFailed("google flow failed")
        viewModel.uiState.value.error shouldBe "google flow failed"
    }

    @Test
    fun `continueAsGuest success authenticates user`() = runTest {
        whenever(authRepository.signInAnonymously())
            .thenReturn(Result.success("uid-guest"))

        viewModel.continueAsGuest()
        advanceUntilIdle()

        viewModel.uiState.value.isAuthenticated.shouldBeTrue()
    }

    @Test
    fun `sendEmailVerification success clears loading without error`() = runTest {
        whenever(authRepository.sendEmailVerification())
            .thenReturn(Result.success(Unit))

        viewModel.sendEmailVerification()
        advanceUntilIdle()

        viewModel.uiState.value.isLoading.shouldBeFalse()
        viewModel.uiState.value.error shouldBe null
    }

    @Test
    fun `sendEmailVerification failure sets mapped error`() = runTest {
        whenever(authRepository.sendEmailVerification())
            .thenReturn(Result.failure(Exception("network unavailable")))

        viewModel.sendEmailVerification()
        advanceUntilIdle()

        viewModel.uiState.value.error.shouldNotBeNull()
    }

    @Test
    fun `checkEmailVerification updates verification flag and current user`() = runTest {
        whenever(authRepository.isEmailVerified()).thenReturn(true)
        whenever(authRepository.getCurrentUserId()).thenReturn("uid-verified")
        whenever(authRepository.getCurrentUserEmail()).thenReturn("verified@email.com")
        whenever(authRepository.getCurrentDisplayName()).thenReturn("Verified User")
        whenever(authRepository.reloadUser()).thenReturn(Result.success(Unit))

        viewModel.checkEmailVerification()
        advanceUntilIdle()

        viewModel.uiState.value.isEmailVerified.shouldBeTrue()
        viewModel.uiState.value.currentUser.shouldNotBeNull()
        viewModel.uiState.value.currentUser?.uid shouldBe "uid-verified"
    }
}
