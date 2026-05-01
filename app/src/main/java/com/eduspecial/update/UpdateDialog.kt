package com.eduspecial.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun UpdateDialogHost(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.resumePendingUpdate()
    }

    when (val s = state) {
        is UpdateState.UpdateAvailable -> {
            UpdateAvailableDialog(
                release = s.release,
                onUpdate = { viewModel.startDownload(s.apkUrl, s.release.tagName.trimStart('v')) },
                onDismiss = viewModel::dismissUpdate
            )
        }

        is UpdateState.PermissionRequired -> {
            val settingsIntent = rememberInstallPermissionIntent(context)
            LaunchedEffect(s.apkUrl) {
                settingsLauncher.launch(settingsIntent)
            }
            InstallPermissionDialog(
                onOpenSettings = { settingsLauncher.launch(settingsIntent) },
                onRetry = viewModel::resumePendingUpdate
            )
        }

        is UpdateState.Downloading -> {
            DownloadProgressDialog(progress = s.progress)
        }

        is UpdateState.ReadyToInstall -> {
            LaunchedEffect(s.apkPath) {
                delay(700)
                context.findActivity()?.finishAndRemoveTaskCompat()
            }
            InstallingDialog()
        }

        is UpdateState.Error -> {
            UpdateErrorDialog(
                message = s.message,
                onDismiss = viewModel::resetError
            )
        }

        else -> Unit
    }
}

private fun rememberInstallPermissionIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

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
        title = { Text("تحديث جديد متاح", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "الإصدار ${release.tagName} جاهز للتثبيت.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onUpdate, shape = RoundedCornerShape(12.dp)) {
                Text("تحديث الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لاحقا")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun InstallPermissionDialog(
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("تفعيل التحديث التلقائي", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Android يحتاج موافقتك مرة واحدة للسماح لـ Flashino بتثبيت التحديثات. بعد تفعيل السماح سيكمل التطبيق تنزيل وتثبيت آخر إصدار.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                Text("فتح الإعدادات")
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("أكملت التفعيل")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DownloadProgressDialog(progress: Int) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("جاري تنزيل التحديث", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${progress.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun InstallingDialog() {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("جاري فتح التثبيت", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "سيغلق التطبيق الآن وتظهر شاشة تثبيت التحديث من Android.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {},
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun UpdateErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعذر التحديث", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("حسنا")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Activity.finishAndRemoveTaskCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAndRemoveTask()
    } else {
        finish()
    }
}
