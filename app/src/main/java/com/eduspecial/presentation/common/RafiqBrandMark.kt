package com.eduspecial.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.eduspecial.presentation.theme.EduThemeExtras

@Composable
fun RafiqBrandMark(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val tokens = EduThemeExtras.tokens
    val glowScale: Float
    val floatY: Float
    val rotation: Float
    if (animated) {
        val infiniteTransition = rememberInfiniteTransition(label = "rafiq_mark")
        glowScale = infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rafiq_glow"
        ).value
        floatY = infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rafiq_float"
        ).value
        rotation = infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rafiq_rotation"
        ).value
    } else {
        glowScale = 1f
        floatY = 0f
        rotation = 0f
    }

    val clickModifier = if (onClick != null) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        modifier
    }

    Box(
        modifier = clickModifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(glowScale)
                .graphicsLayer { translationY = floatY }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = tokens.brandGlow
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .graphicsLayer { translationY = floatY }
                .rotate(rotation)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        tokens.brandCoreGradient
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.55f)
                .graphicsLayer { translationY = floatY }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .graphicsLayer { translationY = floatY }
                .rotate(-36f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        tokens.brandSparkGradient
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.18f)
                .graphicsLayer { translationY = floatY }
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .fillMaxSize(0.14f)
                .scale(glowScale)
                .clip(CircleShape)
                .background(tokens.brandSparkGradient.last())
        )
    }
}
