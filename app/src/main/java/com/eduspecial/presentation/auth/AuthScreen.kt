package com.eduspecial.presentation.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import android.app.Activity
import android.view.SoundEffectConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.eduspecial.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.RafiqBrandMark
import com.eduspecial.presentation.common.rememberResponsiveLayoutInfo
import com.eduspecial.presentation.navigation.Screen
import com.eduspecial.presentation.theme.EduThemeExtras

@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val themeTokens = EduThemeExtras.tokens
    val responsive = rememberResponsiveLayoutInfo()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val webClientId by viewModel.webClientId.collectAsState()
    val googleSignInEnabled by viewModel.isGoogleSignInEnabled.collectAsState()
    val effectiveGoogleClientId = webClientId.trim()
    val showGoogleButton = googleSignInEnabled

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrBlank()) {
                    viewModel.onGoogleSignInFailed(localizedText(context, "تعذر الحصول على رمز Google", "Unable to get the Google token"))
                } else {
                    viewModel.signInWithGoogle(idToken)
                }
            } catch (e: ApiException) {
                viewModel.onGoogleSignInFailed(
                    localizedText(
                        context,
                        "فشل تسجيل الدخول عبر Google (الرمز ${e.statusCode})",
                        "Google sign-in failed (code ${e.statusCode})"
                    )
                )
            }
        } else {
            viewModel.onGoogleSignInFailed(localizedText(context, "تم إلغاء تسجيل الدخول عبر Google", "Google sign-in was cancelled"))
        }
    }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Auth.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    themeTokens.heroGradient
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(8.dp))

            RafiqBrandMark(
                modifier = Modifier.size(72.dp),
                animated = true,
                onClick = {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                    text = stringResource(id = R.string.brand_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = localizedText("دخول ذكي. مراجعة أسرع. مجتمع أقرب.", "Smarter sign-in. Faster review. Closer community."),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))

            // Card
            Card(
                modifier = Modifier
                    .widthIn(max = responsive.formMaxWidth)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ),
                elevation = CardDefaults.cardElevation(18.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {

                    // Tab Row
                    TabRow(
                        selectedTabIndex = if (uiState.isLoginMode) 0 else 1,
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = uiState.isLoginMode,
                            onClick = viewModel::switchToLogin,
                            text = { Text(stringResource(R.string.login)) }
                        )
                        Tab(
                            selected = !uiState.isLoginMode,
                            onClick = viewModel::switchToRegister,
                            text = { Text(stringResource(R.string.register)) }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Display Name (register only)
                    AnimatedVisibility(visible = !uiState.isLoginMode) {
                        Column {
                            OutlinedTextField(
                                value = uiState.displayName,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = { Text(stringResource(R.string.display_name)) },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Email
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(stringResource(R.string.email)) },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = uiState.emailError != null,
                        supportingText = uiState.emailError?.let { { Text(it) } }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Password
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(R.string.password)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (uiState.isLoginMode) viewModel.login()
                                else viewModel.register()
                            }
                        ),
                        isError = uiState.passwordError != null,
                        supportingText = uiState.passwordError?.let { { Text(it) } }
                    )

                    // Error message
                    AnimatedVisibility(visible = uiState.error != null) {
                        uiState.error?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Submit Button
                    com.eduspecial.presentation.common.PrimaryButton(
                        text = if (uiState.isLoginMode) stringResource(R.string.sign_in) else stringResource(R.string.create_account),
                        onClick = {
                            if (uiState.isLoginMode) viewModel.login()
                            else viewModel.register()
                        },
                        isLoading = uiState.isLoading,
                        enabled = !uiState.isLoading,
                        contentDesc = localizedText(
                            context,
                            if (uiState.isLoginMode) "زر تسجيل الدخول" else "زر إنشاء حساب",
                            if (uiState.isLoginMode) "Sign in button" else "Create account button"
                        )
                    )

                    // Google Sign-In
                    if (showGoogleButton) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                localizedText("  أو  ", "  OR  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val gso = GoogleSignInOptions
                                    .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(effectiveGoogleClientId)
                                    .requestEmail()
                                    .build()
                                val client = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(client.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(localizedText("المتابعة باستخدام Google", "Continue with Google"))
                        }
                    }

                    // Guest mode
                    Spacer(Modifier.height(12.dp))
                    if (uiState.isLoginMode) {
                        TextButton(
                            onClick = viewModel::sendPasswordReset,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.forgot_password))
                        }
                    }
                    TextButton(
                        onClick = viewModel::continueAsGuest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.browse_as_guest))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
