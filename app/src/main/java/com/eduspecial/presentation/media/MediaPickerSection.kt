package com.eduspecial.presentation.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.eduspecial.domain.model.MediaType

/**
 * Composable that handles picking and uploading media (image/video/audio)
 * to Cloudinary. Used inside the AddFlashcard and EditFlashcard dialogs.
 */
@Composable
fun MediaPickerSection(
    mediaUrl: String?,
    mediaType: MediaType,
    onMediaSelected: (url: String, type: MediaType) -> Unit,
    onMediaCleared: () -> Unit,
    onAudioPick: (() -> Unit)? = null,
    viewModel: MediaUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadMedia(it, isVideo = false) }
    }

    // Video picker
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadMedia(it, isVideo = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "وسائط (اختياري)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (mediaUrl != null && mediaType != MediaType.NONE) {
            // Preview of selected media
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                when (mediaType) {
                    MediaType.IMAGE -> {
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "صورة محددة",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "فيديو محدد",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text("فيديو محدد",
                                modifier = Modifier.padding(top = 56.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MediaType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = "صوت محدد",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text("صوت محدد",
                                modifier = Modifier.padding(top = 56.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {}
                }
                // Clear button
                IconButton(
                    onClick = onMediaCleared,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إزالة الوسائط", modifier = Modifier.size(18.dp))
                }
            }
        } else {
            // Upload options
            if (uiState.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { uiState.uploadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "جاري الرفع... ${uiState.uploadProgress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Image pick
                    MediaTypeButton(
                        icon = Icons.Default.Image,
                        label = "صورة",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    // Video pick
                    MediaTypeButton(
                        icon = Icons.Default.VideoLibrary,
                        label = "فيديو",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                    // Audio pick
                    MediaTypeButton(
                        icon = Icons.Default.AudioFile,
                        label = "صوت",
                        modifier = Modifier.weight(1f),
                        onClick = { onAudioPick?.invoke() }
                    )
                }
            }

            uiState.error?.let { error ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = { viewModel.clearError() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("إعادة المحاولة", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Unified media type button — icon stacked above label, perfectly centered.
 * Fixed height + softWrap=false + overflow=Clip ensures Arabic labels never wrap or clip.
 */
@Composable
private fun MediaTypeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        // Enough height for icon (20dp) + spacer (4dp) + text (~16dp) + vertical padding (2×6dp)
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
            )
        }
    }
}
