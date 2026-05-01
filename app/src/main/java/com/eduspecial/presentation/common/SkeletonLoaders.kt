package com.eduspecial.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Base shimmer skeleton loader composable.
 * Cycles between surfaceVariant and surface colors with an infinite shimmer animation.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

/**
 * Skeleton matching the shape of FlashcardItem.
 */
@Composable
fun FlashcardItemSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkeletonLoader(modifier = Modifier.fillMaxWidth(0.6f).height(18.dp))
        SkeletonLoader(modifier = Modifier.fillMaxWidth().height(14.dp))
        SkeletonLoader(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLoader(modifier = Modifier.width(80.dp).height(28.dp).clip(RoundedCornerShape(50)))
            SkeletonLoader(modifier = Modifier.width(60.dp).height(28.dp).clip(RoundedCornerShape(50)))
        }
    }
}

/**
 * Skeleton matching the shape of a question card.
 */
@Composable
fun QuestionCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkeletonLoader(modifier = Modifier.fillMaxWidth(0.9f).height(18.dp))
        SkeletonLoader(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLoader(modifier = Modifier.width(90.dp).height(28.dp).clip(RoundedCornerShape(50)))
            Spacer(Modifier.weight(1f))
            SkeletonLoader(modifier = Modifier.width(50.dp).height(28.dp).clip(RoundedCornerShape(50)))
            SkeletonLoader(modifier = Modifier.width(70.dp).height(28.dp).clip(RoundedCornerShape(50)))
        }
    }
}

/**
 * Skeleton matching the shape of a stat card on the Home screen.
 */
@Composable
fun StatCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        SkeletonLoader(modifier = Modifier.width(40.dp).height(28.dp))
        SkeletonLoader(modifier = Modifier.width(60.dp).height(12.dp))
    }
}

/**
 * Skeleton matching the shape of the WeeklyBarChart.
 */
@Composable
fun ChartSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkeletonLoader(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp))
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom
        ) {
            val heights = listOf(0.6f, 0.8f, 0.4f, 1.0f, 0.7f, 0.5f, 0.9f)
            heights.forEach { h ->
                SkeletonLoader(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(h)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
            }
        }
    }
}
