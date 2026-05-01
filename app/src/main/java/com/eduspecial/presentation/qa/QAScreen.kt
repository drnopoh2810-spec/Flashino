package com.eduspecial.presentation.qa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.eduspecial.core.ads.AdFeedItem
import com.eduspecial.core.ads.AdInsertionStrategy
import com.eduspecial.domain.model.QAAnswer
import com.eduspecial.domain.model.QAQuestion
import com.eduspecial.presentation.common.ads.AdContainerView
import com.eduspecial.presentation.common.BottomAwareSnackbarHost
import com.eduspecial.presentation.common.LottieEmptyState
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.QuestionCardSkeleton
import com.eduspecial.presentation.common.RafiqBrandMark
import com.eduspecial.presentation.navigation.Screen
import com.eduspecial.presentation.theme.EduThemeExtras
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QAScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    focusQuestionId: String? = null,
    showAddDialogOnStart: Boolean = false,
    showBackButton: Boolean = false,
    viewModel: QAViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val questions by viewModel.questions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val expandedQuestionId by viewModel.expandedQuestionId.collectAsState()
    val bookmarkedIds by viewModel.bookmarkedQuestionIds.collectAsState()
    val likedQuestionIds by viewModel.likedQuestionIds.collectAsState()
    val likedAnswerIds by viewModel.likedAnswerIds.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentUserId = viewModel.currentUserId
    val context = LocalContext.current
    val displayedQuestions = remember(questions, focusQuestionId) {
        if (focusQuestionId.isNullOrBlank()) questions
        else questions.filter { it.id == focusQuestionId }
    }
    val feedItems = remember(displayedQuestions, focusQuestionId) {
        if (focusQuestionId.isNullOrBlank()) {
            AdInsertionStrategy.injectNativeAds(
                items = displayedQuestions,
                slotPrefix = "qa"
            )
        } else {
            displayedQuestions.map { AdFeedItem.Content(it) }
        }
    }

    var showAddDialog by remember(showAddDialogOnStart) { mutableStateOf(showAddDialogOnStart) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var answerDialogQuestion by remember { mutableStateOf<QAQuestion?>(null) }
    var replyingToAnswer by remember { mutableStateOf<QAAnswer?>(null) }
    var editingQuestion by remember { mutableStateOf<QAQuestion?>(null) }
    var editingAnswer by remember { mutableStateOf<QAAnswer?>(null) }
    var deletingAnswer by remember { mutableStateOf<QAAnswer?>(null) }
    var deletingQuestion by remember { mutableStateOf<QAQuestion?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            com.eduspecial.utils.SyncWorker.triggerImmediateSync(context)
            viewModel.refreshFromServer()
            isRefreshing = false
        }
    }

    LaunchedEffect(focusQuestionId) {
        if (!focusQuestionId.isNullOrBlank()) {
            viewModel.focusQuestion(focusQuestionId)
        }
    }

    LaunchedEffect(showAddDialogOnStart) {
        if (showAddDialogOnStart) {
            showAddDialog = true
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Long
        )
        viewModel.clearError()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(localizedText("الأسئلة والأجوبة", "Questions & Answers"), fontWeight = FontWeight.Bold) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(Icons.Default.Search, contentDescription = localizedText("بحث في الأسئلة", "Search questions"))
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = localizedText("اطرح سؤالًا", "Ask a question"))
                    }
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = localizedText("رجوع", "Back"))
                        }
                    }
                } else {
                    {}
                }
            )
        },
        floatingActionButton = {
            if (focusQuestionId == null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(localizedText("اطرح سؤالًا", "Ask a question")) }
                )
            }
        },
        snackbarHost = { BottomAwareSnackbarHost(snackbarHostState, innerPadding) },
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (focusQuestionId == null) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; viewModel.showAll() }
                        ) {
                            Text(localizedText("الكل", "All"), modifier = Modifier.padding(vertical = 12.dp))
                        }
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; viewModel.showUnanswered() }
                        ) {
                            Text(localizedText("بدون إجابة", "Unanswered"), modifier = Modifier.padding(vertical = 12.dp))
                        }


                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 20.dp,
                        bottom = innerPadding.calculateBottomPadding() + if (focusQuestionId == null) 80.dp else 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (focusQuestionId == null) {
                        item {
                            QAHeroCard(
                                questionCount = displayedQuestions.size,
                                showingUnanswered = selectedTab == 1
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        items(3) { QuestionCardSkeleton() }
                    } else if (displayedQuestions.isEmpty()) {
                        item {
                            LottieEmptyState(
                                message = if (focusQuestionId == null) localizedText("لا توجد أسئلة بعد", "No questions yet") else localizedText("تعذر العثور على السؤال المطلوب", "Unable to find the requested question"),
                                actionLabel = localizedText("اطرح أول سؤال", "Ask the first question"),
                                onAction = { showAddDialog = true }
                            )
                        }
                    } else {
                        items(
                            items = feedItems,
                            key = { item ->
                                when (item) {
                                    is AdFeedItem.Content -> item.value.id
                                    is AdFeedItem.Native -> item.slotKey
                                }
                            }
                        ) { item ->
                            when (item) {
                                is AdFeedItem.Content -> {
                                    val question = item.value
                                    QuestionCard(
                                        question = question,
                                        currentUserId = currentUserId,
                                        isAdmin = isAdmin,
                                        isBookmarked = question.id in bookmarkedIds,
                                        isLiked = question.id in likedQuestionIds,
                                        likedAnswerIds = likedAnswerIds,
                                        isExpanded = expandedQuestionId == question.id,
                                        onUpvote = { viewModel.upvoteQuestion(question.id) },
                                        onAnswer = { answerDialogQuestion = question },
                                        onToggleExpand = { viewModel.toggleExpanded(question.id) },
                                        onBookmark = { viewModel.toggleBookmark(question.id) },
                                        onEdit = { editingQuestion = question },
                                        onDelete = { deletingQuestion = question },
                                        onOpenDetail = {
                                            if (focusQuestionId == null) {
                                                navController.navigate(Screen.QAFocus.createRoute(question.id))
                                            }
                                        },
                                        onUpvoteAnswer = { answerId -> viewModel.upvoteAnswer(answerId) },
                                        onAcceptAnswer = { answerId -> viewModel.acceptAnswer(answerId, question.id) },
                                        onEditAnswer = { answer -> editingAnswer = answer },
                                        onDeleteAnswer = { answer -> deletingAnswer = answer },
                                        onReplyToAnswer = { answer ->
                                            answerDialogQuestion = question
                                            replyingToAnswer = question.answers.firstOrNull {
                                                it.id == (answer.parentAnswerId ?: answer.id)
                                            } ?: answer
                                        }
                                    )
                                }
                                is AdFeedItem.Native -> {
                                    AdContainerView(slotKey = item.slotKey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddQuestionDialog(
            uiState = uiState,
            onQuestionChange = viewModel::onQuestionChange,
            onHashtagsChange = viewModel::onHashtagsChange,
            onSubmit = viewModel::submitQuestion,
            onDismiss = { showAddDialog = false }
        )
    }

    answerDialogQuestion?.let { question ->
        AddAnswerDialog(
            question = question,
            replyingToAnswer = replyingToAnswer,
            uiState = uiState,
            onAnswerChange = viewModel::onAnswerChange,
            onSubmit = { viewModel.submitAnswer(question.id, replyingToAnswer?.id) },
            onDismiss = {
                answerDialogQuestion = null
                replyingToAnswer = null
            }
        )
    }

    val wasSubmitting = remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isSubmitting) {
        if (wasSubmitting.value && !uiState.isSubmitting && uiState.error == null) {
            showAddDialog = false
            answerDialogQuestion = null
            replyingToAnswer = null
        }
        wasSubmitting.value = uiState.isSubmitting
    }

    editingQuestion?.let { question ->
        EditQuestionDialog(
            question = question,
            onSubmit = { text, hashtags ->
                viewModel.editQuestion(question, text, hashtags)
                editingQuestion = null
            },
            onDismiss = { editingQuestion = null }
        )
    }

    editingAnswer?.let { answer ->
        EditAnswerDialog(
            answer = answer,
            onSubmit = { content ->
                viewModel.editAnswer(answer, content)
                editingAnswer = null
            },
            onDismiss = { editingAnswer = null }
        )
    }

    deletingQuestion?.let { question ->
        AlertDialog(
            onDismissRequest = { deletingQuestion = null },
            title = { Text(localizedText("حذف السؤال", "Delete question"), fontWeight = FontWeight.Bold) },
            text = { Text(localizedText("سيتم حذف السؤال من جهازك ومن المحتوى العام.", "This question will be deleted from your device and from the shared content.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuestion(question)
                        deletingQuestion = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(localizedText("حذف", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingQuestion = null }) {
                    Text(localizedText("إلغاء", "Cancel"))
                }
            }
        )
    }

    deletingAnswer?.let { answer ->
        AlertDialog(
            onDismissRequest = { deletingAnswer = null },
            title = { Text(localizedText("حذف الإجابة", "Delete answer"), fontWeight = FontWeight.Bold) },
            text = { Text(localizedText("سيتم حذف الإجابة من جهازك ومن المحتوى العام.", "This answer will be deleted from your device and from the shared content.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnswer(answer)
                        deletingAnswer = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(localizedText("حذف", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnswer = null }) {
                    Text(localizedText("إلغاء", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun QuestionCard(
    question: QAQuestion,
    currentUserId: String,
    isAdmin: Boolean,
    isBookmarked: Boolean,
    isLiked: Boolean,
    likedAnswerIds: Set<String>,
    isExpanded: Boolean,
    onUpvote: () -> Unit,
    onAnswer: () -> Unit,
    onToggleExpand: () -> Unit,
    onBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
    onUpvoteAnswer: (String) -> Unit,
    onAcceptAnswer: (String) -> Unit,
    onEditAnswer: (QAAnswer) -> Unit,
    onDeleteAnswer: (QAAnswer) -> Unit,
    onReplyToAnswer: (QAAnswer) -> Unit
) {
    val canManageQuestion = isAdmin || currentUserId == question.contributor
    var showQuestionMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AuthorAvatar(
                    name = question.contributorName,
                    avatarUrl = question.contributorAvatarUrl
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AuthorName(
                                name = question.contributorName,
                                isVerified = question.contributorVerified,
                                style = MaterialTheme.typography.titleSmall,
                                iconSize = 16.dp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatCommunityDate(question.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (question.isAnswered) {
                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = localizedText("تمت الإجابة", "Answered"),
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            localizedText("مُجاب", "Answered"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(onClick = onBookmark, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isBookmarked) localizedText("إلغاء الحفظ", "Remove bookmark") else localizedText("حفظ", "Save"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (canManageQuestion) {
                                Box {
                                    IconButton(
                                        onClick = { showQuestionMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = localizedText("إدارة السؤال", "Manage question"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showQuestionMenu,
                                        onDismissRequest = { showQuestionMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(localizedText("تعديل السؤال", "Edit question")) },
                                            onClick = {
                                                showQuestionMenu = false
                                                onEdit()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(localizedText("حذف السؤال", "Delete question")) },
                                            onClick = {
                                                showQuestionMenu = false
                                                onDelete()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuestionActionButton(
                    modifier = Modifier.weight(1f),
                    icon = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                    label = localizedText("${question.upvotes} إعجاب", "${question.upvotes} likes"),
                    active = isLiked,
                    onClick = onUpvote
                )
                QuestionActionButton(
                    modifier = Modifier.weight(1f),
                    icon = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    label = localizedText("${question.answers.count { it.parentAnswerId == null }} رد", "${question.answers.count { it.parentAnswerId == null }} replies"),
                    active = isExpanded,
                    onClick = onToggleExpand
                )
                TextButton(
                    onClick = onAnswer,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = localizedText("أضف ردًا", "Add a reply"),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(localizedText("أضف ردًا", "Add a reply"), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (question.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    question.hashtags.take(3).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("#$tag", style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }

            // Inline answer thread
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                AnswerThreadSection(
                    question = question,
                    answers = question.answers.sortedByDescending { it.isAccepted },
                    likedAnswerIds = likedAnswerIds,
                    currentUserId = currentUserId,
                    isAdmin = isAdmin,
                    onUpvote = onUpvoteAnswer,
                    onAccept = onAcceptAnswer,
                    onEdit = onEditAnswer,
                    onDelete = onDeleteAnswer,
                    onReply = onReplyToAnswer
                )
            }
        }
    }
}

@Composable
private fun AnswerThreadSection(
    question: QAQuestion,
    answers: List<QAAnswer>,
    likedAnswerIds: Set<String>,
    currentUserId: String,
    isAdmin: Boolean,
    onUpvote: (String) -> Unit,
    onAccept: (String) -> Unit,
    onEdit: (QAAnswer) -> Unit,
    onDelete: (QAAnswer) -> Unit,
    onReply: (QAAnswer) -> Unit
) {
    val topLevelAnswers = answers.filter { it.parentAnswerId == null }
    val repliesByParent = answers.filter { it.parentAnswerId != null }.groupBy { it.parentAnswerId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider()
        if (topLevelAnswers.isEmpty()) {
            Text(
                localizedText("لا توجد إجابات بعد", "No answers yet"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            topLevelAnswers.forEach { answer ->
                AnswerItem(
                    answer = answer,
                    isLiked = answer.id in likedAnswerIds,
                    isQuestionAuthor = currentUserId == question.contributor,
                    isAnswerAuthor = isAdmin || currentUserId == answer.contributor,
                    replyCount = repliesByParent[answer.id].orEmpty().size,
                    onUpvote = { onUpvote(answer.id) },
                    onAccept = { onAccept(answer.id) },
                    onEdit = { onEdit(answer) },
                    onDelete = { onDelete(answer) },
                    onReply = { onReply(answer) }
                )
                repliesByParent[answer.id].orEmpty().forEach { reply ->
                    Box(modifier = Modifier.padding(start = 28.dp)) {
                        AnswerItem(
                            answer = reply,
                            isLiked = reply.id in likedAnswerIds,
                            isQuestionAuthor = false,
                            isAnswerAuthor = isAdmin || currentUserId == reply.contributor,
                            replyCount = 0,
                            onUpvote = { onUpvote(reply.id) },
                            onAccept = {},
                            onEdit = { onEdit(reply) },
                            onDelete = { onDelete(reply) },
                            onReply = { onReply(reply) },
                            isNestedReply = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerItem(
    answer: QAAnswer,
    isLiked: Boolean,
    isQuestionAuthor: Boolean,
    isAnswerAuthor: Boolean,
    replyCount: Int,
    onUpvote: () -> Unit,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    isNestedReply: Boolean = false
) {
    var showAnswerMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (answer.isAccepted)
            Color(0xFF2E7D32).copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isNestedReply) 0.42f else 0.72f),
        shape = RoundedCornerShape(if (isNestedReply) 18.dp else 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                AuthorAvatar(
                    name = answer.contributorName,
                    avatarUrl = answer.contributorAvatarUrl,
                    size = 36.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AuthorName(
                                name = answer.contributorName,
                                isVerified = answer.contributorVerified,
                                style = MaterialTheme.typography.labelLarge,
                                iconSize = 14.dp
                            )
                            Text(
                                text = formatCommunityDate(answer.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (answer.isAccepted) {
                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = localizedText("إجابة مقبولة", "Accepted answer"),
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = localizedText("مقبولة", "Accepted"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                            if (isAnswerAuthor || (isQuestionAuthor && !answer.isAccepted)) {
                                Box {
                                    IconButton(
                                        onClick = { showAnswerMenu = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = localizedText("إدارة الإجابة", "Manage answer"),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAnswerMenu,
                                        onDismissRequest = { showAnswerMenu = false }
                                    ) {
                                        if (isQuestionAuthor && !answer.isAccepted) {
                                            DropdownMenuItem(
                                                text = { Text(localizedText("قبول الإجابة", "Accept answer")) },
                                                onClick = {
                                                    showAnswerMenu = false
                                                    onAccept()
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF2E7D32)
                                                    )
                                                }
                                            )
                                        }
                                        if (isAnswerAuthor) {
                                            DropdownMenuItem(
                                                text = { Text(localizedText("تعديل الإجابة", "Edit answer")) },
                                                onClick = {
                                                    showAnswerMenu = false
                                                    onEdit()
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Edit, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(localizedText("حذف الإجابة", "Delete answer")) },
                                                onClick = {
                                                    showAnswerMenu = false
                                                    onDelete()
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = answer.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onUpvote,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isLiked) Color(0xFF1E88E5) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = localizedText("تصويت", "Vote"),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(localizedText("${answer.upvotes} إعجاب", "${answer.upvotes} likes"), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = onReply,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = localizedText("الرد على الإجابة", "Reply to answer"), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(localizedText("رد", "Reply"), style = MaterialTheme.typography.labelMedium)
                        }
                        if (!isNestedReply && replyCount > 0) {
                            Text(
                                text = localizedText("$replyCount رد", "$replyCount replies"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (isQuestionAuthor && !answer.isAccepted) {
                            TextButton(
                                onClick = onAccept,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32))
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = localizedText("قبول الإجابة", "Accept answer"),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(localizedText("قبول", "Accept"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QAHeroCard(
    questionCount: Int,
    showingUnanswered: Boolean
) {
    val themeTokens = EduThemeExtras.tokens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        themeTokens.heroGradient
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedText("مجتمع الأسئلة", "Community questions"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (showingUnanswered) {
                        localizedText("الأسئلة المفتوحة أمامك الآن لتضيف لها إجابات مفيدة.", "Open questions waiting for helpful answers from you.")
                    } else {
                        localizedText("اسأل، ناقش، وراجع الإجابات داخل مساحة مجتمعية مرتبة.", "Ask, discuss, and review answers in an organized community space.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.74f)
                )
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            localizedText("$questionCount سؤال ظاهر", "$questionCount visible questions"),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledLabelColor = Color.White
                    ),
                    border = null
                )
            }
            RafiqBrandMark(
                modifier = Modifier.size(44.dp),
                animated = true
            )
        }
    }
}
@Composable
private fun QuestionActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = if (active) activeColor.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AuthorName(
    name: String,
    isVerified: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = style,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isVerified) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = localizedText("حساب موثق", "Verified account"),
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun AuthorAvatar(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = localizedText("صورة الناشر", "Author avatar"),
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatCommunityDate(date: java.util.Date): String {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditQuestionDialog(
    question: QAQuestion,
    onSubmit: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(question.question) }
    var hashtags by remember { mutableStateOf(question.hashtags.joinToString(" ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("تعديل السؤال", "Edit question"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(localizedText("السؤال", "Question")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text(localizedText("هاشتاجات", "Hashtags")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(localizedText("مثال: #تخاطب #ABA #نصائح", "Example: #speech #ABA #tips")) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        text,
                        hashtags
                            .split(" ", ",")
                            .map { it.trim().removePrefix("#") }
                            .filter { it.isNotBlank() }
                            .distinct()
                    )
                },
                enabled = text.isNotBlank()
            ) { Text(localizedText("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) } }
    )
}

@Composable
private fun EditAnswerDialog(
    answer: QAAnswer,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf(answer.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("تعديل الإجابة", "Edit answer"), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(localizedText("الإجابة", "Answer")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(content) },
                enabled = content.isNotBlank()
            ) { Text(localizedText("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddQuestionDialog(
    uiState: QAUiState,
    onQuestionChange: (String) -> Unit,
    onHashtagsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("اطرح سؤالًا", "Ask a question"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.newQuestion,
                    onValueChange = onQuestionChange,
                    label = { Text(localizedText("سؤالك", "Your question")) },
                    isError = uiState.isDuplicate,
                    supportingText = {
                        when {
                            uiState.isDuplicate -> Text(
                                localizedText("سؤال مشابه موجود بالفعل", "A similar question already exists"),
                                color = MaterialTheme.colorScheme.error
                            )
                            uiState.isCheckingDuplicate -> Text(localizedText("جارٍ التحقق...", "Checking..."))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = uiState.newHashtags,
                    onValueChange = onHashtagsChange,
                    label = { Text(localizedText("هاشتاجات", "Hashtags")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(localizedText("مثال: #تخاطب #ABA #نصائح", "Example: #speech #ABA #tips")) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = uiState.newQuestion.isNotBlank() &&
                        !uiState.isDuplicate &&
                        !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(localizedText("نشر", "Post"))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) } }
    )
}

@Composable
private fun AddAnswerDialog(
    question: QAQuestion,
    replyingToAnswer: QAAnswer?,
    uiState: QAUiState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("إضافة إجابة", "Add an answer"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                replyingToAnswer?.let { replyTarget ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = localizedText("رد على ${replyTarget.contributorName}", "Replying to ${replyTarget.contributorName}"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = replyTarget.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = uiState.newAnswer,
                    onValueChange = onAnswerChange,
                    label = { Text(if (replyingToAnswer == null) localizedText("إجابتك", "Your answer") else localizedText("اكتب ردك", "Write your reply")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = uiState.newAnswer.isNotBlank() && !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(localizedText("إرسال", "Send"))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) } }
    )
}



