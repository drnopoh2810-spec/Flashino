package com.eduspecial.presentation.common

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Composable that embeds an ExoPlayer for HLS video or audio playback.
 * Properly handles lifecycle pause/resume and resource cleanup.
 */
@Composable
fun MediaPlayerView(
    url: String,
    modifier: Modifier = Modifier,
    isAudio: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> { /* don't auto-resume */ }
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .height(if (isAudio) 56.dp else 200.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                createPlayerView(ctx, exoPlayer, isAudio)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(UnstableApi::class)
private fun createPlayerView(context: android.content.Context, exoPlayer: ExoPlayer, isAudio: Boolean): PlayerView {
    return PlayerView(context).apply {
        player = exoPlayer
        useController = true
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (isAudio) {
            controllerShowTimeoutMs = 0
            controllerHideOnTouch = false
        }
    }
}

/**
 * Thumbnail for a video card with play overlay.
 */
@Composable
fun VideoThumbnail(
    thumbnailUrl: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Video thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        FilledIconButton(
            onClick = onPlayClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
}
