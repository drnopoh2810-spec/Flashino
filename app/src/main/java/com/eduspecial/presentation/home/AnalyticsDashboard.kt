package com.eduspecial.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduspecial.domain.model.CategoryMastery
import com.eduspecial.domain.model.DailyProgress
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.presentation.flashcards.categoryArabicName
import java.time.LocalDate

@Composable
fun AnalyticsDashboard(
    streak: Int,
    weeklyProgress: List<DailyProgress>,
    categoryMastery: List<CategoryMastery>,
    todayReviewed: Int,
    dailyGoal: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "إحصائياتك",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (isLoading) {
            // Skeleton placeholders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.eduspecial.presentation.common.StatCardSkeleton(Modifier.weight(1f))
                com.eduspecial.presentation.common.StatCardSkeleton(Modifier.weight(1f))
                com.eduspecial.presentation.common.StatCardSkeleton(Modifier.weight(1f))
            }
            com.eduspecial.presentation.common.ChartSkeleton()
        } else {
            // Streak card
            StreakCard(streak = streak)

            // Weekly bar chart
            WeeklyBarChart(weeklyProgress = weeklyProgress)

            // Daily goal progress
            DailyGoalProgressBar(
                todayReviewed = todayReviewed,
                dailyGoal = dailyGoal
            )

            // Category mastery
            if (categoryMastery.isNotEmpty()) {
                CategoryMasteryList(masteryList = categoryMastery)
            }
        }
    }
}

@Composable
fun StreakCard(streak: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF6F00).copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = "سلسلة المراجعة",
                tint = Color(0xFFFF6F00),
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6F00)
                )
                Text(
                    text = "يوم متتالي",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyBarChart(
    weeklyProgress: List<DailyProgress>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val today = LocalDate.now()

    // Build 7-day list (fill missing days with 0)
    val last7Days = (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val epoch = date.toEpochDay()
        val count = weeklyProgress.find { it.dayEpoch == epoch }?.reviewCount ?: 0
        Pair(date, count)
    }

    val maxCount = last7Days.maxOfOrNull { it.second } ?: 1

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "المراجعات الأسبوعية",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                last7Days.forEach { (date, count) ->
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val barHeight = size.height * fraction.coerceAtLeast(0.04f)
                            drawRoundRect(
                                color = barColor.copy(alpha = if (date == today) 1f else 0.5f),
                                topLeft = Offset(0f, size.height - barHeight),
                                size = Size(size.width, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = arabicDayAbbrev(date),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun arabicDayAbbrev(date: LocalDate): String {
    return when (date.dayOfWeek.value) {
        1 -> "إث"
        2 -> "ثل"
        3 -> "أر"
        4 -> "خم"
        5 -> "جم"
        6 -> "سب"
        7 -> "أح"
        else -> ""
    }
}

@Composable
fun DailyGoalProgressBar(
    todayReviewed: Int,
    dailyGoal: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (dailyGoal > 0) (todayReviewed.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
    val isGoalMet = todayReviewed >= dailyGoal
    val successColor = MaterialTheme.colorScheme.secondary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoalMet)
                successColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الهدف اليومي",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$todayReviewed / $dailyGoal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isGoalMet) successColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isGoalMet) successColor else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (isGoalMet) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "أحسنت! لقد حققت هدفك اليومي 🎉",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun CategoryMasteryList(
    masteryList: List<CategoryMastery>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "إتقان الفئات",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            masteryList.forEach { mastery ->
                CategoryMasteryRow(mastery = mastery)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryMasteryRow(mastery: CategoryMastery) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = categoryArabicName(mastery.category),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(mastery.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { mastery.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
