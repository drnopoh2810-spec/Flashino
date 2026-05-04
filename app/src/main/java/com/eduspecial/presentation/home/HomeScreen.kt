package com.eduspecial.presentation.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.R
import com.eduspecial.core.ads.AdManager
import com.eduspecial.core.ads.findActivity
import com.eduspecial.presentation.common.BottomAwareSnackbarHost
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.RafiqBrandMark
import com.eduspecial.presentation.common.ChartSkeleton
import com.eduspecial.presentation.common.StatCardSkeleton
import com.eduspecial.presentation.navigation.Screen
import com.eduspecial.presentation.theme.EduThemeExtras
import kotlinx.coroutines.launch

private val HomeHeroGold = Color(0xFFAB0028)
private val HomeHeroNavy = Color(0xFF050E3C)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val themeTokens = EduThemeExtras.tokens
    val colorScheme = MaterialTheme.colorScheme
    val stats by viewModel.stats.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val weeklyProgress by viewModel.weeklyProgress.collectAsState()
    val categoryMastery by viewModel.categoryMastery.collectAsState()
    val todayReviewed by viewModel.todayReviewed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val adManager = remember(context.applicationContext) { AdManager.getInstance(context) }
    val rewardedReady by adManager.rewardedReady.collectAsState()
    val rewardedLoading by adManager.rewardedLoading.collectAsState()

    val hasData by remember { derivedStateOf { stats.totalFlashcards > 0 } }
    val goalProgress by remember {
        derivedStateOf {
            if (dailyGoal > 0) (todayReviewed.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadAnalytics()
            kotlinx.coroutines.delay(1000)
            isRefreshing = false
        }
    }
    LaunchedEffect(adManager) {
        adManager.preloadRewarded()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
            ) {
                item {
                    HeroHeader(
                        todayReviewed = todayReviewed,
                        dailyGoal = dailyGoal,
                        goalProgress = goalProgress,
                        onSearchClick = { navController.navigate(Screen.Search.route) }
                    )
                }

                item {
                    Crossfade(
                        targetState = isLoading,
                        animationSpec = tween(300),
                        label = "stats_crossfade"
                    ) { loading ->
                        if (loading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCardSkeleton(Modifier.weight(1f))
                                StatCardSkeleton(Modifier.weight(1f))
                                StatCardSkeleton(Modifier.weight(1f))
                            }
                        } else {
                            StatsRow(stats)
                        }
                    }
                }

                item {
                    SectionHeading(localizedText("إجراءات سريعة", "Quick actions"))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            QuickActionCard(
                                icon = Icons.Default.School,
                                label = localizedText("ابدأ المراجعة", "Start study"),
                                color = colorScheme.primary
                            ) { navController.navigate(Screen.Study.route) }
                        }
                        item {
                            QuickActionCard(
                                icon = Icons.Default.Add,
                                label = localizedText("أضف مصطلحًا", "Add term"),
                                color = lerp(colorScheme.secondary, colorScheme.primary, 0.18f)
                            ) { navController.navigate(Screen.FlashcardsCompose.route) }
                        }
                        item {
                            QuickActionCard(
                                icon = Icons.Default.QuestionAnswer,
                                label = localizedText("اطرح سؤالًا", "Ask a question"),
                                color = lerp(colorScheme.tertiary, colorScheme.error, 0.24f)
                            ) { navController.navigate(Screen.QACompose.route) }
                        }
                        item {
                            QuickActionCard(
                                icon = Icons.Default.Bookmark,
                                label = localizedText("المحفوظات", "Bookmarks"),
                                color = lerp(colorScheme.primary, colorScheme.tertiary, 0.42f)
                            ) { navController.navigate(Screen.Bookmarks.route) }
                        }
                        item {
                            QuickActionCard(
                                icon = Icons.Default.EmojiEvents,
                                label = localizedText("المتصدرون", "Leaderboard"),
                                color = lerp(colorScheme.secondary, colorScheme.tertiary, 0.30f)
                            ) { navController.navigate(Screen.Leaderboard.route) }
                        }
                        item {
                            QuickActionCard(
                                icon = Icons.Default.Archive,
                                label = localizedText("المتقنة", "Mastered"),
                                color = lerp(colorScheme.primary, colorScheme.error, 0.20f)
                            ) { navController.navigate(Screen.Flashcards.route) }
                        }
                    }
                }

                item {
                    RewardedSupportCard(
                        isRewardedReady = rewardedReady,
                        isRewardedLoading = rewardedLoading,
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(localizedText(context, "تعذر فتح إعلان الدعم الآن", "Unable to open the support ad right now"))
                                }
                            } else {
                                adManager.showRewardedAd(
                                    activity = activity,
                                    onRewardEarned = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(localizedText(context, "شكرًا لدعمك للتطبيق", "Thanks for supporting the app"))
                                        }
                                    },
                                    onUnavailable = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(localizedText(context, "تعذر تحميل إعلان الدعم الآن. سيحاول التطبيق تجهيزه مرة أخرى.", "The support ad could not load right now. The app will try preparing it again."))
                                        }
                                    }
                                )
                            }
                        }
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Crossfade(
                        targetState = isLoading,
                        animationSpec = tween(300),
                        label = "analytics_crossfade"
                    ) { loading ->
                        if (loading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = localizedText("إحصائياتك", "Your analytics"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ChartSkeleton()
                            }
                        } else {
                            AnalyticsDashboard(
                                streak = streak,
                                weeklyProgress = weeklyProgress,
                                categoryMastery = categoryMastery,
                                todayReviewed = todayReviewed,
                                dailyGoal = dailyGoal,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }

        BottomAwareSnackbarHost(
            hostState = snackbarHostState,
            innerPadding = innerPadding,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HeroHeader(
    todayReviewed: Int,
    dailyGoal: Int,
    goalProgress: Float,
    onSearchClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomeHeroNavy)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 26.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                shape = RoundedCornerShape(999.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))
            ) {
                Text(
                    text = localizedText("فلاشينو اليوم", "Today's Flashino"),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.brand_name),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = localizedText("نبض التعلم والمراجعة الذكية", "The pulse of learning and smart review"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.app_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        lineHeight = 22.sp
                    )
                }
                RafiqBrandMark(
                    modifier = Modifier.size(72.dp),
                    animated = true
                )
            }
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = localizedText("إيقاع اليوم", "Today's rhythm"),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$todayReviewed من $dailyGoal بطاقة",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = localizedText("استمر بنفس الإيقاع لتبقى مراجعتك اليومية ثابتة وواضحة.", "Keep the same rhythm so your daily review stays clear and consistent."),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = HomeHeroGold,
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = localizedText("بحث", "Search"),
                        tint = HomeHeroGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            localizedText("ابحث بسرعة", "Quick search"),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            localizedText("ابحث عن مصطلح أو مفهوم أو سؤال...", "Search for a term, concept, or question..."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(HomeHeroGold)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun StatsRow(stats: HomeStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = localizedText("إجمالي المصطلحات", "Total terms"),
            value = stats.totalFlashcards.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = localizedText("المتقنة", "Mastered"),
            value = stats.mastered.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = localizedText("للمراجعة", "To review"),
            value = stats.toReview.toString()
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .fillMaxWidth(0.58f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                HomeHeroGold
                            )
                        )
                    )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(118.dp)
            .height(112.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun RewardedSupportCard(
    isRewardedReady: Boolean,
    isRewardedLoading: Boolean,
    onClick: () -> Unit
) {
    val themeTokens = EduThemeExtras.tokens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                themeTokens.heroGradient.last().copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedText("ادعم استمرار التطبيق", "Support the app"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = localizedText("إعلان مكافأة اختياري ولن يظهر تلقائيًا أثناء التصفح أو المراجعة.", "This rewarded ad is optional and will not appear automatically while browsing or studying."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onClick
            ) {
                Text(
                    when {
                        isRewardedReady -> localizedText("شاهد الآن", "Watch now")
                        isRewardedLoading -> localizedText("جاري التحميل", "Loading")
                        else -> localizedText("اضغط للتجهيز", "Tap to prepare")
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryListItem(category: HomeCategoryItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "تصفح فئة ${category.name}",
                onClick = onClick
            )
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "${category.name}: ${category.description}" },
        headlineContent = { Text(category.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(category.description, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = category.color
                )
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
