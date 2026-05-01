package com.eduspecial.presentation.splash

import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduspecial.R
import com.eduspecial.presentation.theme.EduThemeExtras
import kotlin.math.min

@Composable
fun LaunchIntroOverlay(
    modifier: Modifier = Modifier
) {
    val themeTokens = EduThemeExtras.tokens
    val context = LocalContext.current
    val reveal = androidx.compose.runtime.remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "launch_intro")
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "launch_pulse"
    )

    LaunchedEffect(Unit) {
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val canPlay = audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0)
        var player: MediaPlayer? = null
        if (canPlay) {
            player = MediaPlayer.create(context, R.raw.rafiq_intro_chime)?.apply {
                setVolume(0.28f, 0.28f)
                setOnCompletionListener { mp ->
                    mp.setOnCompletionListener(null)
                    mp.release()
                }
                start()
            }
        }
        onDispose {
            player?.runCatching {
                setOnCompletionListener(null)
                if (isPlaying) stop()
                release()
            }
        }
    }

    val backgroundTop = themeTokens.heroGradient.first()
    val backgroundBottom = themeTokens.splashBottom
    val contentAlpha = reveal.value
    val contentScale = 0.84f + ((1f - 0.84f) * reveal.value)
    val contentOffset = (1f - reveal.value) * 38f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeTokens.splashOrbital.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.58f),
                    radius = size.minDimension * 0.42f
                ),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.5f, size.height * 0.58f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeTokens.splashWarm.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.44f),
                    radius = size.minDimension * 0.2f
                ),
                radius = size.minDimension * 0.2f,
                center = Offset(size.width * 0.74f, size.height * 0.44f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(contentAlpha)
                .scale(contentScale)
                .offset { IntOffset(0, contentOffset.toInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RafiqLaunchGlyph(
                modifier = Modifier.size(188.dp),
                orbitalRotation = orbitalRotation,
                pulse = pulse
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.76f)
                ),
                modifier = Modifier.alpha(contentAlpha)
            )
        }
    }
}

@Composable
private fun RafiqLaunchGlyph(
    modifier: Modifier = Modifier,
    orbitalRotation: Float,
    pulse: Float
) {
    val themeTokens = EduThemeExtras.tokens
    Canvas(
        modifier = modifier.scale(pulse)
    ) {
        val minDimension = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = minDimension * 0.42f
        val innerRadius = minDimension * 0.29f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                center = center,
                radius = outerRadius * 1.45f
            ),
            radius = outerRadius * 1.45f,
            center = center
        )

        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    themeTokens.splashOrbital.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.88f),
                    themeTokens.splashWarm.copy(alpha = 0.88f),
                    themeTokens.splashOrbital.copy(alpha = 0.12f)
                ),
                center = center
            ),
            startAngle = orbitalRotation,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = minDimension * 0.06f, cap = StrokeCap.Round)
        )

        drawCircle(
            color = Color.White,
            radius = outerRadius * 0.86f,
            center = center
        )

        drawCircle(
            color = themeTokens.brandCoreGradient.first(),
            radius = innerRadius,
            center = center
        )

        rotate(degrees = 45f, pivot = center) {
            drawRoundRect(
                color = themeTokens.splashWarm,
                topLeft = Offset(center.x - minDimension * 0.17f, center.y - minDimension * 0.17f),
                size = Size(minDimension * 0.34f, minDimension * 0.34f),
                cornerRadius = CornerRadius(minDimension * 0.06f, minDimension * 0.06f)
            )
        }

        drawCircle(
            color = Color.White,
            radius = minDimension * 0.033f,
            center = center,
            blendMode = BlendMode.SrcOver
        )
    }
}
