package com.eduspecial.presentation.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.presentation.common.rememberResponsiveLayoutInfo
import com.eduspecial.presentation.navigation.Screen

// ─── Permission definitions ───────────────────────────────────────────────────

data class AppPermission(
    val permission: String,
    val icon: ImageVector,
    val title: String,
    val reason: String,
    val isRequired: Boolean = false
)

/** All permissions the app needs, with human-readable Arabic explanations */
val requiredPermissions: List<AppPermission> = buildList {
    // Notifications — Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(AppPermission(
            permission  = Manifest.permission.POST_NOTIFICATIONS,
            icon        = Icons.Default.Notifications,
            title       = "الإشعارات",
            reason      = "لتذكيرك بمواعيد المراجعة اليومية",
            isRequired  = false
        ))
    }
    // Media — Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(AppPermission(
            permission  = Manifest.permission.READ_MEDIA_IMAGES,
            icon        = Icons.Default.Image,
            title       = "الصور",
            reason      = "لإضافة صور للبطاقات التعليمية وصورة الملف الشخصي",
            isRequired  = false
        ))
        add(AppPermission(
            permission  = Manifest.permission.READ_MEDIA_VIDEO,
            icon        = Icons.Default.VideoLibrary,
            title       = "الفيديو",
            reason      = "لإضافة مقاطع فيديو للبطاقات التعليمية",
            isRequired  = false
        ))
        add(AppPermission(
            permission  = Manifest.permission.READ_MEDIA_AUDIO,
            icon        = Icons.Default.AudioFile,
            title       = "الصوت",
            reason      = "لإضافة مقاطع صوتية للبطاقات التعليمية",
            isRequired  = false
        ))
    } else {
        // Android 12 and below
        add(AppPermission(
            permission  = Manifest.permission.READ_EXTERNAL_STORAGE,
            icon        = Icons.Default.FolderOpen,
            title       = "الوسائط",
            reason      = "للوصول إلى الصور والفيديو والصوت لإضافتها للبطاقات",
            isRequired  = false
        ))
    }
    // Camera
    add(AppPermission(
        permission  = Manifest.permission.CAMERA,
        icon        = Icons.Default.CameraAlt,
        title       = "الكاميرا",
        reason      = "لالتقاط صور مباشرة وإضافتها للبطاقات",
        isRequired  = false
    ))
    // Microphone
    add(AppPermission(
        permission  = Manifest.permission.RECORD_AUDIO,
        icon        = Icons.Default.Mic,
        title       = "الميكروفون",
        reason      = "لتسجيل مقاطع صوتية وإضافتها للبطاقات",
        isRequired  = false
    ))
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun PermissionRequestScreen(
    navController: NavController,
    nextRoute: String,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val responsive = rememberResponsiveLayoutInfo()
    val permissionsToRequest = remember {
        requiredPermissions.map { it.permission }.toTypedArray()
    }

    // Batch launcher — requests all permissions at once
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Mark done regardless of grant result — user made their choice
        viewModel.markPermissionsDone()
        navController.navigate(nextRoute) {
            popUpTo(Screen.Permissions.route) { inclusive = true }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "الأذونات المطلوبة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "يحتاج التطبيق إلى بعض الأذونات لتقديم تجربة كاملة.\nجميعها اختيارية ويمكنك تغييرها لاحقاً من الإعدادات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Permission list
            Card(
                modifier = Modifier
                    .widthIn(max = responsive.formMaxWidth)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    requiredPermissions.forEachIndexed { index, perm ->
                        PermissionRow(perm)
                        if (index < requiredPermissions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Buttons
            Column(
                modifier = Modifier
                    .widthIn(max = responsive.formMaxWidth)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { launcher.launch(permissionsToRequest) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("منح الأذونات", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = {
                        viewModel.markPermissionsDone()
                        navController.navigate(nextRoute) {
                            popUpTo(Screen.Permissions.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "تخطي في الوقت الحالي",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: AppPermission) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    perm.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        headlineContent = {
            Text(perm.title, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                perm.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
