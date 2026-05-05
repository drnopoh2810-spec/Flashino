package com.eduspecial.presentation.flashcards

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.SRSResult
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.LottieEmptyState
import com.eduspecial.presentation.common.MediaPlayerView
import com.eduspecial.presentation.theme.EduThemeExtras
import com.eduspecial.utils.InAppReviewManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    navController: NavController,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Trigger in-app review when session completes with meaningful progress
    val sessionComplete = uiState.currentCard == null && uiState.reviewedThisSession > 0
    LaunchedEffect(sessionComplete) {
        if (sessionComplete && activity != null) {
            InAppReviewManager(activity).requestReview()
        }
    }

    // Stop TTS when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSpeaking() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(localizedText("وضع المراجعة", "Study mode"), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopSpeaking()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = localizedText("رجوع", "Back"))
                    }
                },
                actions = {
                            // Card counter - vertically centered with the icon via Row alignment
                    if (uiState.totalCards > 0) {
                        Text(
                            text = "${uiState.currentIndex + 1}/${uiState.totalCards}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 4.dp)
                        )
                    }
                    // TTS toggle button
                    IconButton(
                        onClick = { viewModel.toggleTts() },
                        modifier = Modifier.semantics {
                            contentDescription = if (uiState.ttsEnabled) {
                                localizedText(context, "إيقاف النطق التلقائي", "Disable auto pronunciation")
                            } else {
                                localizedText(context, "تفعيل النطق التلقائي", "Enable auto pronunciation")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (uiState.ttsEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (uiState.totalCards > 0) {
                LinearProgressIndicator(
                    progress = {
                        if (uiState.totalCards > 0)
                            uiState.currentIndex.toFloat() / uiState.totalCards
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(16.dp))
            }

            if (uiState.selectedGroup == null) {
                GroupSelectionPlaceholder(
                    groups = uiState.availableGroups,
                    onSelectGroup = viewModel::selectGroup
                )
            } else if (uiState.currentCard != null) {
                Text(
                    text = localizedText("المجموعة الحالية: ${uiState.selectedGroup}", "Current group: ${uiState.selectedGroup}"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FlashcardStudyCard(
                        card = uiState.currentCard!!,
                        isFlipped = uiState.isFlipped,
                        isSpeaking = uiState.isSpeaking,
                        ttsEnabled = uiState.ttsEnabled,
                        onFlip = viewModel::flipCard,
                        onSpeakTerm = viewModel::speakCurrentTerm,
                        onSpeakDefinition = viewModel::speakCurrentDefinition
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (!uiState.isFlipped) {
                    Text(
                        text = localizedText("اضغط على البطاقة لإظهار التعريف", "Tap the card to reveal the definition"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isFlipped,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    SRSActionButtons(
                        onAgain = { viewModel.processReview(SRSResult.Again) },
                        onHard  = { viewModel.processReview(SRSResult.Hard) },
                        onGood  = { viewModel.processReview(SRSResult.Good) },
                        onEasy  = { viewModel.processReview(SRSResult.Easy) }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    StudyCompletePlaceholder(
                        mastered = uiState.masteredThisSession,
                        reviewed = uiState.reviewedThisSession,
                        onRestart = viewModel::restartSession
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSelectionPlaceholder(
    groups: List<String>,
    onSelectGroup: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = localizedText("اختر المجموعة التي تود دراستها", "Choose the group you want to study"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = localizedText("ستراجع كل البطاقات المستحقة داخل هذه المجموعة بدون حد يومي مؤقتًا.", "You will review all due cards in this group with no temporary daily cap."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (groups.isEmpty()) {
            Text(
                text = localizedText("لا توجد مجموعات بعد. أضف مصطلحات داخل مجموعة أولًا.", "No groups yet. Add terms inside a group first."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { group ->
                    ElevatedAssistChip(
                        onClick = { onSelectGroup(group) },
                        label = { Text(group) }
                    )
                }
            }
        }
    }
}
// â”€â”€â”€ Flashcard Study Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun FlashcardStudyCard(
    card: Flashcard,
    isFlipped: Boolean,
    isSpeaking: Boolean,
    ttsEnabled: Boolean,
    onFlip: () -> Unit,
    onSpeakTerm: () -> Unit,
    onSpeakDefinition: () -> Unit
) {
    val themeTokens = EduThemeExtras.tokens
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val flippedDescription = localizedText(context, "البطاقة مقلوبة - التعريف: ${card.definition}", "Card flipped - definition: ${card.definition}")
    val termDescription = localizedText(context, "المصطلح: ${card.term} - اضغط لإظهار التعريف", "Term: ${card.term} - tap to reveal the definition")

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_flip"
    )

    val showBack = rotation > 90f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onFlip()
            }
            .semantics {
                contentDescription = if (isFlipped) flippedDescription else termDescription
            },
        contentAlignment = Alignment.Center
    ) {
        if (!showBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(themeTokens.heroGradient)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = localizedText("المصطلح", "Term"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = card.term,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    TtsSpeakerButton(
                        isSpeaking = isSpeaking,
                        ttsEnabled = ttsEnabled,
                        onTap = onSpeakTerm,
                        label = localizedText("استمع للنطق", "Listen to pronunciation"),
                        isOnDarkBackground = true
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = localizedText("التعريف", "Definition"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = card.definition,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (card.mediaType != MediaType.NONE && card.mediaUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        when (card.mediaType) {
                            MediaType.IMAGE -> {
                                coil.compose.AsyncImage(
                                    model = card.mediaUrl,
                                    contentDescription = localizedText("صورة البطاقة", "Card image"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            MediaType.VIDEO -> {
                                MediaPlayerView(
                                    url = card.mediaUrl,
                                    isAudio = false,
                                    modifier = Modifier.fillMaxWidth().height(96.dp)
                                )
                            }
                            MediaType.AUDIO -> {
                                MediaPlayerView(
                                    url = card.mediaUrl,
                                    isAudio = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            else -> {}
                        }
                    }

                    if (card.mediaType == MediaType.NONE || card.mediaType == MediaType.IMAGE) {
                        Spacer(Modifier.height(12.dp))
                        TtsSpeakerButton(
                            isSpeaking = isSpeaking,
                            ttsEnabled = ttsEnabled,
                            onTap = onSpeakDefinition,
                            label = localizedText("استمع للتعريف", "Listen to definition"),
                            isOnDarkBackground = false
                        )
                    }
                }
            }
        }
    }
}
// â”€â”€â”€ TTS Speaker Button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Animated speaker button that pulses while TTS is speaking.
 * Tapping it manually triggers speech.
 */
@Composable
private fun TtsSpeakerButton(
    isSpeaking: Boolean,
    ttsEnabled: Boolean,
    onTap: () -> Unit,
    label: String,
    isOnDarkBackground: Boolean
) {
    val pulseScale = if (isSpeaking) {
        val infiniteTransition = rememberInfiniteTransition(label = "tts_pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        animatedScale
    } else {
        1f
    }

    val iconColor = if (isOnDarkBackground)
        Color.White.copy(alpha = 0.9f)
    else
        MaterialTheme.colorScheme.primary

    val bgColor = if (isOnDarkBackground)
        Color.White.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.primaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(if (isSpeaking) pulseScale else 1f)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = onTap)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isSpeaking) localizedText("جارٍ النطق...", "Speaking...") else label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isOnDarkBackground) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// â”€â”€â”€ SRS Action Buttons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SRSActionButtons(
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    onEasy: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val themeTokens = EduThemeExtras.tokens

    data class SRSButton(
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val color: Color,
        val a11y: String,
        val onClick: () -> Unit
    )

    val buttons = listOf(
        SRSButton(localizedText("مجددًا", "Again"), Icons.Default.Replay, MaterialTheme.colorScheme.error, localizedText("مجددًا - لم أتذكر", "Again - I did not remember it"), onAgain),
        SRSButton(localizedText("صعب", "Hard"), Icons.Default.Warning, themeTokens.heroGradient.last(), localizedText("صعب - تذكرت بصعوبة", "Hard - I remembered it with difficulty"), onHard),
        SRSButton(localizedText("جيد", "Good"), Icons.Default.ThumbUp, MaterialTheme.colorScheme.primary, localizedText("جيد - تذكرت بشكل جيد", "Good - I remembered it well"), onGood),
        SRSButton(localizedText("سهل", "Easy"), Icons.Default.Archive, MaterialTheme.colorScheme.secondary, localizedText("سهل - أتقنت هذا المصطلح", "Easy - I mastered this term"), onEasy)
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = localizedText("كيف كانت معرفتك بهذا المصطلح؟", "How well did you know this term?"),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val buttonRows = if (maxWidth < 420.dp) buttons.chunked(2) else listOf(buttons)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttonRows.forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowButtons.forEach { btn ->
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    btn.onClick()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .semantics { contentDescription = btn.a11y },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = btn.color),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = btn.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = btn.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (rowButtons.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
// â”€â”€â”€ Session Complete â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun StudyCompletePlaceholder(mastered: Int, reviewed: Int, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieEmptyState(
            message = localizedText("أحسنت! لا توجد بطاقات للمراجعة اليوم", "Well done! There are no cards to review today"),
            actionLabel = localizedText("بدء جلسة جديدة", "Start a new session"),
            onAction = onRestart
        )
        Spacer(Modifier.height(8.dp))
        Text(localizedText("راجعت: $reviewed بطاقة", "Reviewed: $reviewed cards"), style = MaterialTheme.typography.bodyLarge)
        Text(
            localizedText("أتقنت: $mastered بطاقة", "Mastered: $mastered cards"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
