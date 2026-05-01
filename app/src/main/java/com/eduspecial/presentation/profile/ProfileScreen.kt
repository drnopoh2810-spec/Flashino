package com.eduspecial.presentation.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.eduspecial.R
import com.eduspecial.core.ads.AdManager
import com.eduspecial.core.ads.findActivity
import com.eduspecial.presentation.common.BottomAwareSnackbarHost
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.RafiqBrandMark
import com.eduspecial.presentation.navigation.Screen
import com.eduspecial.presentation.theme.EduThemeExtras
import com.eduspecial.update.UpdateState
import com.eduspecial.update.UpdateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    updateViewModel: UpdateViewModel,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val themeTokens = EduThemeExtras.tokens
    val tag = "ProfileScreen"
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val adManager = remember(context.applicationContext) { AdManager.getInstance(context) }
    val rewardedLoading by adManager.rewardedLoading.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    var manualUpdateCheckRequested by remember { mutableStateOf(false) }
    val updateFlowActive = updateState is UpdateState.Checking ||
        updateState is UpdateState.Downloading ||
        updateState is UpdateState.PermissionRequired ||
        updateState is UpdateState.ReadyToInstall
    val currentLanguageLabel = stringResource(languageLabelRes(uiState.language))

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        adManager.preloadRewarded()
    }

    // Show avatar error snackbar
    LaunchedEffect(uiState.avatarError) {
        uiState.avatarError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearAvatarError()
        }
    }

    LaunchedEffect(updateState, manualUpdateCheckRequested) {
        if (!manualUpdateCheckRequested) return@LaunchedEffect
        when (updateState) {
            UpdateState.UpToDate -> {
                snackbarHostState.showSnackbar(
                    localizedText(context, "أنت تستخدم أحدث إصدار بالفعل", "You already have the latest version")
                )
                manualUpdateCheckRequested = false
                updateViewModel.dismissUpdate()
            }
            is UpdateState.Error,
            is UpdateState.PermissionRequired,
            is UpdateState.Downloading,
            is UpdateState.ReadyToInstall,
            is UpdateState.UpdateAvailable -> {
                manualUpdateCheckRequested = false
            }
            UpdateState.Idle,
            UpdateState.Checking -> Unit
        }
    }

    var showGoalDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    fun requestDailyGoalUnlock() {
        val activity = context.findActivity()
        if (activity == null) {
            scope.launch { snackbarHostState.showSnackbar(localizedText(context, "تعذر فتح إعلان المكافأة الآن", "Unable to open the rewarded ad right now")) }
            return
        }

        adManager.showRewardedUnlockSequence(
            activity = activity,
            onProgress = { completed, required ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        localizedText(context, "تمت مشاهدة $completed من $required إعلانات مطلوبة", "Watched $completed of $required required ads")
                    )
                }
            },
            onRewardEarned = {
                scope.launch {
                    if (viewModel.unlockDailyGoalStep()) {
                        snackbarHostState.showSnackbar(localizedText(context, "تم فتح 10 بطاقات إضافية اليوم", "Unlocked 10 extra cards today"))
                    }
                }
            },
            onUnavailable = {
                scope.launch { snackbarHostState.showSnackbar(localizedText(context, "إعلان المكافأة غير جاهز بعد", "The rewarded ad is not ready yet")) }
            }
        )
    }

    // Password change feedback
    LaunchedEffect(uiState.passwordSuccess) {
        if (uiState.passwordSuccess) {
            snackbarHostState.showSnackbar(localizedText(context, "تم تغيير كلمة المرور بنجاح", "Password changed successfully"))
            viewModel.clearPasswordState()
            showPasswordDialog = false
        }
    }

    // Avatar image picker
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        Log.d(tag, "Avatar picker result uri=$uri")
        uri?.let { viewModel.uploadAvatar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(localizedText("الملف الشخصي", "Profile"), fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { BottomAwareSnackbarHost(snackbarHostState, innerPadding) },
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                themeTokens.heroGradient
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedText("حسابي", "My account"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = localizedText("خصص تجربتك وتابع تقدمك ومساهماتك اليومية.", "Personalize your experience and track your progress and daily contributions."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.76f)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        RafiqBrandMark(
                            modifier = Modifier.size(60.dp),
                            animated = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Avatar section
            AvatarSection(
                avatarUrl = uiState.avatarUrl,
                displayName = uiState.displayName,
                isUploading = uiState.isUploadingAvatar,
                onTap = {
                    avatarPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            // Display name editor
            DisplayNameEditor(
                displayName = uiState.displayName,
                isVerified = uiState.isVerified,
                isEditing = uiState.isEditingName,
                nameError = uiState.nameError,
                isUpdating = uiState.isUpdatingName,
                onEditStart = viewModel::startEditingName,
                onSubmit = viewModel::updateDisplayName,
                onCancel = viewModel::cancelEditingName
            )

            Text(
                text = uiState.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    Modifier.weight(1f),
                    localizedText("المساهمات", "Contributions"),
                    uiState.contributionCount.toString()
                )
                StatChip(
                    Modifier.weight(1f),
                    localizedText("المتقنة", "Mastered"),
                    uiState.masteredCount.toString()
                )
                StatChip(
                    Modifier.weight(1f),
                    localizedText("للمراجعة", "To review"),
                    uiState.reviewCount.toString()
                )
            }

            Spacer(Modifier.height(32.dp))

            // Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.EmojiEvents,
                        title = localizedText("لوحة المتصدرين", "Leaderboard"),
                        onClick = { navController.navigate(Screen.Leaderboard.route) }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Bookmark,
                        title = localizedText("المحفوظات", "Bookmarks"),
                        onClick = { navController.navigate(Screen.Bookmarks.route) }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = localizedText("إعدادات الحساب", "Account settings"),
                        onClick = { navController.navigate(Screen.ProfileSettings.route) }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        title = when (updateState) {
                            UpdateState.Checking -> localizedText("جاري البحث عن تحديث...", "Checking for updates...")
                            is UpdateState.Downloading -> localizedText("جاري تنزيل التحديث...", "Downloading update...")
                            is UpdateState.PermissionRequired -> localizedText("أكمل إذن تثبيت التحديث", "Complete update install permission")
                            is UpdateState.ReadyToInstall -> localizedText("جاري فتح التثبيت", "Opening installer")
                            else -> localizedText("التحقق من وجود تحديثات", "Check for updates")
                        },
                        trailing = if (updateFlowActive) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else null,
                        onClick = if (updateFlowActive) {
                            null
                        } else {
                            {
                                manualUpdateCheckRequested = true
                                updateViewModel.checkForUpdate(autoInstall = true, notifyErrors = true)
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language_title),
                        trailing = {
                            Text(
                                text = currentLanguageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { showLanguageDialog = true }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = localizedText("الوضع الليلي", "Dark mode"),
                        trailing = {
                            Switch(
                                checked = uiState.isDarkMode,
                                onCheckedChange = viewModel::toggleDarkMode
                            )
                        }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = localizedText("إشعارات المراجعة", "Study reminders"),
                        trailing = {
                            Switch(
                                checked = uiState.notificationsEnabled,
                                onCheckedChange = viewModel::toggleNotifications
                            )
                        }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.TrackChanges,
                        title = localizedText("الهدف اليومي: ${uiState.dailyGoal} بطاقات", "Daily goal: ${uiState.dailyGoal} cards"),
                        onClick = { showGoalDialog = true }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = localizedText("تغيير كلمة المرور", "Change password"),
                        onClick = { showPasswordDialog = true }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Logout,
                        title = localizedText("تسجيل الخروج", "Sign out"),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = viewModel::signOut
                    )
                }
            }
        }
    }

    if (showGoalDialog) {
        DailyGoalDialog(
            currentGoal = uiState.dailyGoal,
            maxUnlockedGoal = uiState.dailyGoalCap,
            canUnlockMore = uiState.canUnlockDailyGoal,
            isRewardedReady = !rewardedLoading,
            onConfirm = { goal ->
                viewModel.setDailyGoal(goal)
                showGoalDialog = false
            },
            onUnlockMore = { requestDailyGoalUnlock() },
            onDismiss = { showGoalDialog = false }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            isLoading = uiState.isChangingPassword,
            error = uiState.passwordError,
            onConfirm = { current, new ->
                viewModel.changePassword(current, new)
            },
            onDismiss = {
                viewModel.clearPasswordState()
                showPasswordDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSwitchDialog(
            currentLanguage = uiState.language,
            onConfirm = { selectedLanguage ->
                viewModel.setLanguage(selectedLanguage)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
fun AvatarSection(
    avatarUrl: String?,
    displayName: String,
    isUploading: Boolean,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = localizedText("تغيير صورة الملف الشخصي", "Change profile picture")) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = localizedText("صورة الملف الشخصي", "Profile picture"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Upload progress overlay
        if (isUploading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }
        } else {
            // Camera badge - always visible so the user knows it is tappable
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.padding(2.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayNameEditor(
    displayName: String,
    isVerified: Boolean,
    isEditing: Boolean,
    nameError: String?,
    isUpdating: Boolean,
    onEditStart: () -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var editText by remember(isEditing) { mutableStateOf(displayName) }

    if (isEditing) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                label = { Text(localizedText("الاسم المعروض", "Display name")) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSubmit(editText) },
                    enabled = !isUpdating && editText.isNotBlank()
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(localizedText("حفظ", "Save"))
                    }
                }
                TextButton(onClick = onCancel) { Text(localizedText("إلغاء", "Cancel")) }
            }
        }
    } else {
        val verifiedLabel = localizedText("حساب موثق", "Verified account")
        val editNameLabel = localizedText("تعديل الاسم", "Edit name")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (isVerified) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = verifiedLabel,
                    tint = Color(0xFF1E88E5),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                )
            }
            IconButton(onClick = onEditStart) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = editNameLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun StatChip(modifier: Modifier, label: String, value: String) {
    val themeTokens = EduThemeExtras.tokens
    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .width(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                themeTokens.heroGradient.last()
                            )
                        )
                    )
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null)
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = title, onClick = onClick)
            .semantics { contentDescription = title }
    else
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = title }

    ListItem(
        modifier = modifier,
        headlineContent = { Text(title, color = titleColor) },
        leadingContent = { Icon(icon, contentDescription = null, tint = titleColor.copy(alpha = 0.7f)) },
        trailingContent = trailing
    )
}

@Composable
private fun DailyGoalDialog(
    currentGoal: Int,
    maxUnlockedGoal: Int,
    canUnlockMore: Boolean,
    isRewardedReady: Boolean,
    onConfirm: (Int) -> Unit,
    onUnlockMore: () -> Unit,
    onDismiss: () -> Unit
) {
    var goal by remember { mutableIntStateOf(currentGoal) }
    val options = (20..maxUnlockedGoal step 10).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("الهدف اليومي", "Daily goal"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(localizedText("اختر عدد البطاقات التي تريد مراجعتها يوميًا:", "Choose how many cards you want to review each day:"))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { goal = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = goal == option,
                            onClick = { goal = option }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(localizedText("$option بطاقة يوميًا", "$option cards per day"))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    text = localizedText("الحد المفتوح لك الآن: $maxUnlockedGoal بطاقة يوميًا", "Current unlocked cap: $maxUnlockedGoal cards per day"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canUnlockMore) {
                    FilledTonalButton(
                        onClick = onUnlockMore,
                        enabled = isRewardedReady,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(localizedText("شاهد إعلانين لتزيد من البطاقات (+10)", "Watch 2 ads to add more cards (+10)"))
                    }
                } else {
                    Text(
                        text = localizedText("وصلت إلى الحد الأقصى اليومي: 100 بطاقة", "You reached the maximum daily limit: 100 cards"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(goal) }) { Text(localizedText("حفظ", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) }
        }
    )
}
@Composable
private fun ChangePasswordDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: (currentPassword: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val mismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
    val tooShort = newPassword.isNotEmpty() && newPassword.length < 6
    val canSubmit = currentPassword.isNotBlank() &&
        newPassword.length >= 6 &&
        newPassword == confirmPassword &&
        !isLoading

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(localizedText("تغيير كلمة المرور", "Change password"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(localizedText("كلمة المرور الحالية", "Current password")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showCurrent) localizedText("إخفاء", "Hide") else localizedText("إظهار", "Show")
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(localizedText("كلمة المرور الجديدة", "New password")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(localizedText("6 أحرف على الأقل", "At least 6 characters"), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showNew) localizedText("إخفاء", "Hide") else localizedText("إظهار", "Show")
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(localizedText("تأكيد كلمة المرور الجديدة", "Confirm new password")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(localizedText("كلمتا المرور غير متطابقتين", "Passwords do not match"), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirm = !showConfirm }) {
                            Icon(
                                if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showConfirm) localizedText("إخفاء", "Hide") else localizedText("إظهار", "Show")
                            )
                        }
                    }
                )

                if (error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentPassword, newPassword) },
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(localizedText("تغيير", "Change"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(localizedText("إلغاء", "Cancel"))
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
@Composable
private fun LanguageSwitchDialog(
    currentLanguage: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageOptionRow(
                    label = stringResource(R.string.language_ar),
                    selected = selectedLanguage == "ar",
                    onClick = { selectedLanguage = "ar" }
                )
                LanguageOptionRow(
                    label = stringResource(R.string.language_en),
                    selected = selectedLanguage == "en",
                    onClick = { selectedLanguage = "en" }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLanguage) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@StringRes
private fun languageLabelRes(language: String): Int {
    return if (language == "en") R.string.language_en else R.string.language_ar
}

