package com.eduspecial.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Observes [UpdateViewModel] and shows the appropriate Material 3 dialog
 * for each update state (available, downloading, ready-to-install, error).
 *
 * Drop this composable anywhere in your NavHost root so it's always active.
 */
@Composable
fun UpdateDialogHost(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        is UpdateState.UpdateAvailable -> {
            UpdateAvailableDialog(
                release = s.release,
                onUpdate = {
                    // Check install-unknown-apps permission on Android 8+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    } else {
                        viewModel.startDownload(s.apkUrl, s.release.tagName.trimStart('v'))
                    }
                },
                onDismiss = viewModel::dismissUpdate
            )
        }

        is UpdateState.Downloading -> {
            DownloadProgressDialog(progress = s.progress)
        }

        is UpdateState.ReadyToInstall -> {
            // Install already triggered by ViewModel — show brief confirmation
            ReadyToInstallDialog(onDismiss = viewModel::dismissUpdate)
        }

        is UpdateState.Error -> {
            UpdateErrorDialog(
                message = s.message,
                onDismiss = viewModel::resetError
            )
        }

        else -> { /* Idle / UpToDate / Checking — no dialog needed */ }
    }
}

// ─── Update Available ─────────────────────────────────────────────────────────

@Composable
private fun UpdateAvailableDialog(
    release: GitHubRelease,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "تحديث جديد متاح",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "الإصدار ${release.tagName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!release.body.isNullOrBlank()) {
                    HorizontalDivider()
                    Text(
                        "ملاحظات الإصدار:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = release.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تحديث الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لاحقاً")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ─── Download Progress ────────────────────────────────────────────────────────

@Composable
private fun DownloadProgressDialog(progress: Int) {
    AlertDialog(
        onDismissRequest = { /* non-dismissible while downloading */ },
        title = { Text("جاري التنزيل...", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(16.dp)
    )
}

// ─── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun UpdateErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خطأ في التحديث", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("حسناً")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ─── Ready to Install ─────────────────────────────────────────────────────────

@Composable
private fun ReadyToInstallDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("جاهز للتثبيت", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "تم تنزيل التحديث. اتبع تعليمات التثبيت التي ستظهر على شاشتك.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("حسناً")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
