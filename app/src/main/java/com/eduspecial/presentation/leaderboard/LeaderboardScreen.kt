package com.eduspecial.presentation.leaderboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.eduspecial.domain.model.LeaderboardEntry
import com.eduspecial.domain.model.LeaderboardPeriod
import com.eduspecial.presentation.theme.EduThemeExtras

// ─── Medal Colors ─────────────────────────────────────────────────────────────
private val GoldColor   = Color(0xFFFFD700)
private val SilverColor = Color(0xFFC0C0C0)
private val BronzeColor = Color(0xFFCD7F32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val themeTokens = EduThemeExtras.tokens
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("لوحة المتصدرين", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                )
            ) {
                // ── Period Tabs ────────────────────────────────────────────────
                item {
                    PeriodTabRow(
                        selected = uiState.selectedPeriod,
                        onSelect = viewModel::selectPeriod
                    )
                }

                // ── Loading ────────────────────────────────────────────────────
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    return@LazyColumn
                }

                // ── Error ──────────────────────────────────────────────────────
                if (uiState.error != null) {
                    item {
                        ErrorState(
                            message = uiState.error!!,
                            onRetry = viewModel::refresh
                        )
                    }
                    return@LazyColumn
                }

                // ── Empty ──────────────────────────────────────────────────────
                if (uiState.entries.isEmpty()) {
                    item { EmptyLeaderboard() }
                    return@LazyColumn
                }

                // ── Top 3 Podium ───────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(8.dp))
                    TopThreePodium(entries = uiState.entries.take(3))
                    Spacer(Modifier.height(16.dp))
                }

                // ── Divider ────────────────────────────────────────────────────
                if (uiState.entries.size > 3) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "  باقي المتصدرين  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Ranks 4+ ───────────────────────────────────────────────────
                val restEntries = uiState.entries.drop(3)
                itemsIndexed(restEntries, key = { _, e -> e.userId }) { index, entry ->
                    LeaderboardRow(
                        entry = entry,
                        modifier = Modifier.animateItem()
                    )
                }

                // ── Current user separator (if not in top list) ────────────────
                val currentUserEntry = uiState.entries.lastOrNull { it.isCurrentUser }
                if (currentUserEntry != null && currentUserEntry.rank > uiState.entries.size) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            "ترتيبك",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LeaderboardRow(entry = currentUserEntry)
                    }
                }
            }
        }
    }
}

// ─── Period Tab Row ───────────────────────────────────────────────────────────

@Composable
private fun PeriodTabRow(
    selected: LeaderboardPeriod,
    onSelect: (LeaderboardPeriod) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = LeaderboardPeriod.values().indexOf(selected),
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {}
    ) {
        LeaderboardPeriod.values().forEach { period ->
            Tab(
                selected = selected == period,
                onClick = { onSelect(period) },
                text = { Text(period.label, fontWeight = if (selected == period) FontWeight.Bold else FontWeight.Normal) }
            )
        }
    }
}

// ─── Top 3 Podium ─────────────────────────────────────────────────────────────

@Composable
private fun TopThreePodium(entries: List<LeaderboardEntry>) {
    val themeTokens = EduThemeExtras.tokens
    // Gradient header background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        themeTokens.heroGradient.first().copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // 2nd place (left)
            if (entries.size >= 2) {
                PodiumItem(
                    entry = entries[1],
                    medalColor = SilverColor,
                    podiumHeight = 80.dp,
                    rank = 2,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 1st place (center — tallest)
            if (entries.isNotEmpty()) {
                PodiumItem(
                    entry = entries[0],
                    medalColor = GoldColor,
                    podiumHeight = 110.dp,
                    rank = 1,
                    modifier = Modifier.weight(1f),
                    showCrown = true
                )
            }

            // 3rd place (right)
            if (entries.size >= 3) {
                PodiumItem(
                    entry = entries[2],
                    medalColor = BronzeColor,
                    podiumHeight = 60.dp,
                    rank = 3,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumItem(
    entry: LeaderboardEntry,
    medalColor: Color,
    podiumHeight: androidx.compose.ui.unit.Dp,
    rank: Int,
    modifier: Modifier = Modifier,
    showCrown: Boolean = false
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = "المركز $rank: ${entry.displayName} — ${entry.points} نقطة" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crown for 1st place
        if (showCrown) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = GoldColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.height(32.dp))
        }

        // Avatar
        UserAvatar(
            avatarUrl = entry.avatarUrl,
            displayName = entry.displayName,
            size = if (rank == 1) 72.dp else 56.dp,
            borderColor = medalColor,
            isCurrentUser = entry.isCurrentUser
        )

        Spacer(Modifier.height(6.dp))

        // Name
        Text(
            text = if (entry.isCurrentUser) "أنت" else entry.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (entry.isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (entry.isVerified) {
            Icon(
                Icons.Default.Verified,
                contentDescription = "حساب موثق",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
        }

        // Points
        Text(
            text = "${entry.points} نقطة",
            style = MaterialTheme.typography.labelSmall,
            color = medalColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        // Podium block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(podiumHeight)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(medalColor.copy(alpha = 0.8f), medalColor.copy(alpha = 0.4f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ─── Leaderboard Row (rank 4+) ────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    modifier: Modifier = Modifier
) {
    val bgColor = if (entry.isCurrentUser)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    else
        MaterialTheme.colorScheme.surface

    val borderModifier = if (entry.isCurrentUser)
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(borderModifier)
            .semantics {
                contentDescription = "المركز ${entry.rank}: ${entry.displayName} — ${entry.points} نقطة"
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (entry.isCurrentUser) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank number
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.width(8.dp))

            // Avatar
            UserAvatar(
                avatarUrl = entry.avatarUrl,
                displayName = entry.displayName,
                size = 40.dp,
                borderColor = if (entry.isCurrentUser) MaterialTheme.colorScheme.primary else Color.Transparent,
                isCurrentUser = entry.isCurrentUser
            )

            Spacer(Modifier.width(12.dp))

            // Name + stats
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                    text = if (entry.isCurrentUser) "${entry.displayName} (أنت)" else entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                    if (entry.isVerified) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "حساب موثق",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                ContributionChips(entry = entry)
            }

            Spacer(Modifier.width(8.dp))

            // Points badge
            PointsBadge(points = entry.points, isCurrentUser = entry.isCurrentUser)
        }
    }
}

// ─── Contribution Chips ───────────────────────────────────────────────────────

@Composable
private fun ContributionChips(entry: LeaderboardEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry.flashcardsAdded > 0) {
            MiniChip(
                icon = Icons.Default.Style,
                count = entry.flashcardsAdded,
                color = MaterialTheme.colorScheme.primary,
                label = "بطاقة"
            )
        }
        if (entry.questionsAsked > 0) {
            MiniChip(
                icon = Icons.Default.QuestionAnswer,
                count = entry.questionsAsked,
                color = Color(0xFF7B1FA2),
                label = "سؤال"
            )
        }
        if (entry.acceptedAnswers > 0) {
            MiniChip(
                icon = Icons.Default.CheckCircle,
                count = entry.acceptedAnswers,
                color = MaterialTheme.colorScheme.secondary,
                label = "إجابة مقبولة"
            )
        }
    }
}

@Composable
private fun MiniChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    color: Color,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .semantics { contentDescription = "$count $label" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

// ─── Points Badge ─────────────────────────────────────────────────────────────

@Composable
private fun PointsBadge(points: Int, isCurrentUser: Boolean) {
    val color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = formatPoints(points),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = "نقطة",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
            fontSize = 9.sp
        )
    }
}

private fun formatPoints(points: Int): String = when {
    points >= 1_000_000 -> "${points / 1_000_000}M"
    points >= 1_000     -> "${points / 1_000}K"
    else                -> points.toString()
}

// ─── User Avatar ──────────────────────────────────────────────────────────────

@Composable
private fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    isCurrentUser: Boolean
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = if (borderColor != Color.Transparent) 2.dp else 0.dp,
                color = borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "صورة $displayName",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Initials avatar
            val bgColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else generateAvatarColor(displayName)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
    }
}

/** Generates a consistent color from a display name string */
private fun generateAvatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFF00897B),
        Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFFC62828),
        Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF4527A0)
    )
    val index = (name.hashCode() and Int.MAX_VALUE) % colors.size
    return colors[index]
}

// ─── Empty / Error States ─────────────────────────────────────────────────────

@Composable
private fun EmptyLeaderboard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "لا يوجد متصدرون بعد",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            "كن أول من يضيف مصطلحاً ويتصدر القائمة!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("إعادة المحاولة")
        }
    }
}
