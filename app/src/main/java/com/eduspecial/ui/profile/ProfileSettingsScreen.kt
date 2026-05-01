package com.eduspecial.ui.profile

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduspecial.R
import com.eduspecial.data.model.UserPreferences
import com.eduspecial.data.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: ProfileSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) {
            onAccountDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            uiState.userProfile?.let { profile ->
                ProfileInfoSection(
                    profile = profile,
                    onUpdateProfile = viewModel::updateProfile
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                PreferencesSection(
                    preferences = profile.preferences,
                    onUpdatePreferences = viewModel::updatePreferences,
                    onSendTestReminder = viewModel::sendTestReminder
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                SecuritySection(
                    onChangePassword = { showPasswordDialog = true },
                    onNavigateToSecurity = onNavigateToSecurity
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                DangerZoneSection(
                    onDeleteAccount = { showDeleteAccountDialog = true }
                )
            }

            if (uiState.userProfile == null && !uiState.isLoading) {
                EmptyProfileState(
                    hasError = uiState.error != null,
                    onRetry = viewModel::loadUserProfile
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.successMessage?.let { message ->
                StatusMessageCard(
                    message = message,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onDismiss = viewModel::clearSuccessMessage
                )
            }

            uiState.error?.let { error ->
                StatusMessageCard(
                    message = error,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onDismiss = viewModel::clearError
                )
            }
        }
    }

    if (showPasswordDialog) {
        PasswordChangeDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { currentPassword, newPassword ->
                viewModel.changePassword(currentPassword, newPassword)
                showPasswordDialog = false
            }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onConfirm = {
                viewModel.deleteAccount()
                showDeleteAccountDialog = false
            }
        )
    }
}

@Composable
private fun ProfileInfoSection(
    profile: UserProfile,
    onUpdateProfile: (Map<String, Any>) -> Unit
) {
    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio.orEmpty()) }
    var isEditing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.personal_info),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        if (isEditing) {
                            onUpdateProfile(
                                mapOf(
                                    "displayName" to displayName,
                                    "bio" to bio
                                )
                            )
                        }
                        isEditing = !isEditing
                    }
                ) {
                    Text(if (isEditing) stringResource(R.string.save) else stringResource(R.string.edit))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.display_name_label)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.bio_label)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text(stringResource(R.string.bio_placeholder)) }
                )
            } else {
                ProfileInfoItem(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.display_name_label),
                    value = profile.displayName
                )
                ProfileInfoItem(
                    icon = Icons.Default.Email,
                    label = stringResource(R.string.email_label),
                    value = profile.email,
                    trailing = {
                        Icon(
                            imageVector = if (profile.emailVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = if (profile.emailVerified) stringResource(R.string.verified) else stringResource(R.string.not_verified),
                            tint = if (profile.emailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
                if (profile.bio?.isNotBlank() == true) {
                    ProfileInfoItem(
                        icon = Icons.Default.Info,
                        label = stringResource(R.string.bio_title),
                        value = profile.bio
                    )
                }
                ProfileInfoItem(
                    icon = Icons.Default.Star,
                    label = stringResource(R.string.points_label),
                    value = stringResource(R.string.points_value, profile.points)
                )
                ProfileInfoItem(
                    icon = Icons.Default.Create,
                    label = stringResource(R.string.contributions_label),
                    value = stringResource(R.string.contributions_value, profile.contributionCount)
                )
            }
        }
    }
}

@Composable
private fun PreferencesSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit,
    onSendTestReminder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.preferences_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_more)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                PreferenceItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language_title),
                    subtitle = if (preferences.language == "en") stringResource(R.string.language_en) else stringResource(R.string.language_ar),
                    onClick = { showLanguageDialog = true }
                )

                PreferenceItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme_title),
                    subtitle = when (preferences.theme) {
                        "light" -> stringResource(R.string.theme_light)
                        "dark" -> stringResource(R.string.theme_dark)
                        else -> stringResource(R.string.theme_system)
                    },
                    onClick = { showThemeDialog = true }
                )

                PreferenceItem(
                    icon = Icons.Default.ColorLens,
                    title = stringResource(R.string.theme_palette_title),
                    subtitle = when (preferences.themePalette) {
                        "oasis" -> stringResource(R.string.theme_palette_oasis)
                        "sunset" -> stringResource(R.string.theme_palette_sunset)
                        else -> stringResource(R.string.theme_palette_qusasa)
                    },
                    onClick = { showPaletteDialog = true }
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications_title),
                    subtitle = stringResource(R.string.notifications_subtitle),
                    checked = preferences.notificationsEnabled,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(notificationsEnabled = it)) }
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.study_reminders_title),
                    subtitle = stringResource(R.string.study_reminders_subtitle),
                    checked = preferences.studyRemindersEnabled,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(studyRemindersEnabled = it)) }
                )

                PreferenceItem(
                    icon = Icons.Default.AccessTime,
                    title = stringResource(R.string.reminder_time_title),
                    subtitle = preferences.reminderTime,
                    onClick = {
                        val parts = preferences.reminderTime.split(":")
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onUpdatePreferences(preferences.copy(reminderTime = "%02d:%02d".format(hour, minute)))
                            },
                            parts.getOrNull(0)?.toIntOrNull() ?: 19,
                            parts.getOrNull(1)?.toIntOrNull() ?: 0,
                            true
                        ).show()
                    }
                )

                PreferenceItem(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.test_notification_title),
                    subtitle = stringResource(R.string.test_notification_subtitle),
                    onClick = onSendTestReminder
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.Email,
                    title = stringResource(R.string.email_notifications_title),
                    subtitle = stringResource(R.string.email_notifications_subtitle),
                    checked = preferences.emailNotificationsEnabled,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(emailNotificationsEnabled = it)) }
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.VolumeUp,
                    title = stringResource(R.string.sound_title),
                    subtitle = stringResource(R.string.sound_subtitle),
                    checked = preferences.soundEnabled,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(soundEnabled = it)) }
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.Vibration,
                    title = stringResource(R.string.vibration_title),
                    subtitle = stringResource(R.string.vibration_subtitle),
                    checked = preferences.vibrationEnabled,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(vibrationEnabled = it)) }
                )

                PreferenceSwitchItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.autoplay_tts_title),
                    subtitle = stringResource(R.string.autoplay_tts_subtitle),
                    checked = preferences.autoPlayTTS,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(autoPlayTTS = it)) }
                )
            }
        }
    }

    if (showLanguageDialog) {
        PreferenceChoiceDialog(
            title = stringResource(R.string.language_title),
            currentValue = preferences.language,
            options = listOf(
                "ar" to stringResource(R.string.language_ar),
                "en" to stringResource(R.string.language_en)
            ),
            onDismiss = { showLanguageDialog = false },
            onConfirm = {
                onUpdatePreferences(preferences.copy(language = it))
                showLanguageDialog = false
            }
        )
    }

    if (showThemeDialog) {
        PreferenceChoiceDialog(
            title = stringResource(R.string.theme_title),
            currentValue = preferences.theme,
            options = listOf(
                "system" to stringResource(R.string.theme_system),
                "light" to stringResource(R.string.theme_light),
                "dark" to stringResource(R.string.theme_dark)
            ),
            onDismiss = { showThemeDialog = false },
            onConfirm = {
                onUpdatePreferences(preferences.copy(theme = it))
                showThemeDialog = false
            }
        )
    }

    if (showPaletteDialog) {
        PreferenceChoiceDialog(
            title = stringResource(R.string.theme_palette_title),
            currentValue = preferences.themePalette,
            options = listOf(
                "qusasa" to stringResource(R.string.theme_palette_qusasa),
                "oasis" to stringResource(R.string.theme_palette_oasis),
                "sunset" to stringResource(R.string.theme_palette_sunset)
            ),
            onDismiss = { showPaletteDialog = false },
            onConfirm = {
                onUpdatePreferences(preferences.copy(themePalette = it))
                showPaletteDialog = false
            }
        )
    }
}

@Composable
private fun SecuritySection(
    onChangePassword: () -> Unit,
    onNavigateToSecurity: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            PreferenceItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.change_password_title),
                subtitle = stringResource(R.string.change_password_subtitle),
                onClick = onChangePassword
            )
            PreferenceItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.security_settings_title),
                subtitle = stringResource(R.string.security_settings_subtitle),
                onClick = onNavigateToSecurity
            )
        }
    }
}

@Composable
private fun DangerZoneSection(
    onDeleteAccount: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.danger_zone_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            PreferenceItem(
                icon = Icons.Default.DeleteForever,
                title = stringResource(R.string.delete_account_title),
                subtitle = stringResource(R.string.delete_account_subtitle),
                onClick = onDeleteAccount,
                titleColor = MaterialTheme.colorScheme.error,
                subtitleColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
private fun PreferenceItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.open), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreferenceSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PasswordChangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    val isValid = currentPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(stringResource(R.string.current_password)) },
                    visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                            Icon(
                                if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showCurrentPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showNewPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.password_min_length)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showConfirmPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = confirmPassword.isNotBlank() && newPassword != confirmPassword,
                    supportingText = {
                        if (confirmPassword.isNotBlank() && newPassword != confirmPassword) {
                            Text(stringResource(R.string.password_mismatch))
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentPassword, newPassword) }, enabled = isValid) {
                Text(stringResource(R.string.change))
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
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.delete_account_dialog_title)) },
        text = { Text(stringResource(R.string.delete_account_dialog_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete_forever))
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
private fun StatusMessageCard(
    message: String,
    containerColor: Color,
    contentColor: Color,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss), color = contentColor)
            }
        }
    }
}

@Composable
private fun PreferenceChoiceDialog(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedValue by remember(currentValue) { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedValue = value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedValue == value, onClick = { selectedValue = value })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedValue) }) {
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
private fun EmptyProfileState(
    hasError: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (hasError) stringResource(R.string.empty_profile_error_title) else stringResource(R.string.empty_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.empty_profile_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}
