package com.eduspecial.presentation.flashcards

import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.core.ads.AdFeedItem
import com.eduspecial.core.ads.AdInsertionStrategy
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.MediaType
import com.eduspecial.presentation.common.ads.AdContainerView
import com.eduspecial.presentation.common.BottomAwareSnackbarHost
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.common.RafiqBrandMark
import com.eduspecial.presentation.media.MediaPickerSection
import com.eduspecial.presentation.media.MediaUploadViewModel
import com.eduspecial.presentation.navigation.Screen
import com.eduspecial.presentation.common.LottieEmptyState
import com.eduspecial.presentation.common.FlashcardItemSkeleton
import com.eduspecial.presentation.theme.EduThemeExtras
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(    navController: NavController,
    innerPadding: PaddingValues,
    initialCategory: FlashcardCategory? = null,
    focusFlashcardId: String? = null,
    showAddDialogOnStart: Boolean = false,
    showBackButton: Boolean = false,
    viewModel: FlashcardsViewModel = hiltViewModel()
) {
    val flashcards by viewModel.flashcards.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val audioUiStateById by viewModel.audioUiStateById.collectAsState()
    val audioCountdownById by viewModel.audioCountdownById.collectAsState()
    val groupNames by viewModel.groupNames.collectAsState()
    val currentUserId = viewModel.currentUserId
    val displayedFlashcards = remember(flashcards, focusFlashcardId) {
        if (focusFlashcardId.isNullOrBlank()) flashcards
        else flashcards.filter { it.id == focusFlashcardId }
    }
    val feedItems = remember(displayedFlashcards, focusFlashcardId) {
        if (focusFlashcardId.isNullOrBlank()) {
            AdInsertionStrategy.injectNativeAds(
                items = displayedFlashcards,
                slotPrefix = "flashcards"
            )
        } else {
            displayedFlashcards.map { AdFeedItem.Content(it) }
        }
    }
    var showAddDialog by remember(showAddDialogOnStart) { mutableStateOf(showAddDialogOnStart) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var deletingCard by remember { mutableStateOf<Flashcard?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val addTermA11y = localizedText("إضافة مصطلح جديد", "Add a new term")
    val addSharedTermA11y = localizedText("إضافة مصطلح جديد للقاعدة المشتركة", "Add a new shared term")
    val scope = rememberCoroutineScope()
    var showLibraryMenu by remember { mutableStateOf(false) }
    var pendingExportGroup by remember { mutableStateOf<String?>(null) }
    var confirmedExportGroup by remember { mutableStateOf<String?>(null) }
    var pendingExportFilename by remember { mutableStateOf<String?>(null) }
    var showExportGroupPicker by remember { mutableStateOf(false) }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { content ->
                viewModel.importFromCsv(content)
                    .onSuccess { imported ->
                        snackbarHostState.showSnackbar(
                            if (imported > 0) localizedText(context, "تم استيراد $imported بطاقة", "Imported $imported cards") else localizedText(context, "لم يتم العثور على بطاقات جديدة للاستيراد", "No new cards were found to import")
                        )
                    }
                    .onFailure { error ->
                        snackbarHostState.showSnackbar(error.message ?: localizedText(context, "فشل استيراد الملف", "Failed to import the file"))
                    }
            }.onFailure {
                snackbarHostState.showSnackbar(localizedText(context, "تعذر قراءة ملف CSV", "Unable to read the CSV file"))
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selectedGroup = confirmedExportGroup
        confirmedExportGroup = null
        if (selectedGroup.isNullOrBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.exportSelectedGroupCsv(selectedGroup)
                .onSuccess { csv ->
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                    }.onSuccess {
                        snackbarHostState.showSnackbar(localizedText(context, "تم تصدير مجموعة $selectedGroup", "Exported group $selectedGroup"))
                    }.onFailure {
                        snackbarHostState.showSnackbar(localizedText(context, "تعذر حفظ ملف CSV", "Unable to save the CSV file"))
                    }
                }
                .onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: localizedText(context, "فشل تصدير المجموعة", "Failed to export the group"))
                }
        }
    }

    LaunchedEffect(pendingExportFilename) {
        val filename = pendingExportFilename ?: return@LaunchedEffect
        pendingExportFilename = null
        exportCsvLauncher.launch(filename)
    }

    LaunchedEffect(initialCategory) { }

    LaunchedEffect(showAddDialogOnStart) {
        if (showAddDialogOnStart) {
            showAddDialog = true
        }
    }

    // Handle undo snackbar
    val undoFlashcard = uiState.undoFlashcard
    LaunchedEffect(undoFlashcard) {
        if (undoFlashcard != null) {
            val result = snackbarHostState.showSnackbar(
                message = localizedText(context, "تم حذف البطاقة", "Card deleted"),
                actionLabel = localizedText(context, "تراجع", "Undo"),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        if (showAddDialog) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Long
        )
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedText("البطاقات التعليمية", "Flashcards"), fontWeight = FontWeight.Bold) },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = localizedText("رجوع", "Back"))
                        }
                    }
                } else {
                    {}
                },
                actions = {
                    // زر الإضافة بارز في الـ TopAppBar دائمًا
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.semantics { contentDescription = addTermA11y }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Study.route) }) {
                        Icon(Icons.Default.School, contentDescription = localizedText("مراجعة", "Study"))
                    }
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(Icons.Default.Search, contentDescription = localizedText("بحث", "Search"))
                    }
                    Box {
                        IconButton(onClick = { showLibraryMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = localizedText("خيارات المكتبة", "Library options"))
                        }
                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { showLibraryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(localizedText("استيراد CSV من الجهاز", "Import CSV from device")) },
                                onClick = {
                                    showLibraryMenu = false
                                    importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                                },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(localizedText("افتح مكتبة الموقع", "Open the website library")) },
                                onClick = {
                                    showLibraryMenu = false
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://manoo22.pythonanywhere.com/"))
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(localizedText("تصدير المجموعة الحالية", "Export current group")) },
                                onClick = {
                                    val selectedGroup = uiState.selectedGroupName
                                    when {
                                        !selectedGroup.isNullOrBlank() -> {
                                            pendingExportGroup = selectedGroup
                                            showLibraryMenu = false
                                        }
                                        groupNames.isEmpty() -> {
                                            showLibraryMenu = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar(localizedText(context, "لا توجد مجموعات للتصدير", "There are no groups to export"))
                                            }
                                        }
                                        groupNames.size == 1 -> {
                                            pendingExportGroup = groupNames.first()
                                            showLibraryMenu = false
                                        }
                                        else -> {
                                            showExportGroupPicker = true
                                            showLibraryMenu = false
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // FAB كبير وبارز دائمًا في أسفل الشاشة
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                text = { Text(localizedText("إضافة مصطلح", "Add term"), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.semantics { contentDescription = addSharedTermA11y },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { BottomAwareSnackbarHost(snackbarHostState, innerPadding) },
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->
        // Pull-to-refresh state
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                com.eduspecial.utils.SyncWorker.triggerImmediateSync(context)
                kotlinx.coroutines.delay(1500)
                isRefreshing = false
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FlashcardsHeroCard(
                        totalCards = displayedFlashcards.size,
                        selectedGroupName = uiState.selectedGroupName
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (groupNames.isNotEmpty()) {
                    item {
                        GroupFilterLazyRow(
                            groups = groupNames,
                            selected = uiState.selectedGroupName,
                            onGroupSelected = viewModel::filterByGroup
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (uiState.isLoading) {
                    items(3) { FlashcardItemSkeleton() }
                } else if (displayedFlashcards.isEmpty()) {
                    item {
                        LottieEmptyState(
                            message = if (focusFlashcardId == null) localizedText("لا توجد بطاقات بعد", "No flashcards yet") else localizedText("تعذر العثور على البطاقة المطلوبة", "Unable to find the requested flashcard"),
                            actionLabel = localizedText("أضف أول بطاقة", "Add your first card"),
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
                                val card = item.value
                                if (card.id != uiState.undoFlashcard?.id) {
                                    FlashcardItem(
                                        card = card,
                                        currentUserId = currentUserId,
                                        isAdmin = isAdmin,
                                        isBookmarked = card.id in bookmarkedIds,
                                        onBookmark = { viewModel.toggleBookmark(card.id) },
                                        onEdit = { editingCard = card },
                                        onDelete = { deletingCard = card },
                                        onSpeakTerm = { viewModel.speakTerm(card) },
                                        onSpeakDefinition = { viewModel.speakDefinition(card) },
                                        audioUiState = audioUiStateById[card.id],
                                        audioCountdownSeconds = audioCountdownById[card.id]
                                    )
                                }
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

    if (showAddDialog) {
        AddFlashcardDialog(
            uiState = uiState,
            groupNames = groupNames,
            onTermChange = viewModel::onTermChange,
            onDefinitionChange = viewModel::onDefinitionChange,
            onGroupNameChange = viewModel::onGroupNameChange,
            onSubmit = { mediaUrl, mediaType ->
                viewModel.submitFlashcardWithMedia(mediaUrl, mediaType)
                // Dialog closes automatically when isSubmitting goes false (see LaunchedEffect below)
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearError()
            }
        )
        // Close dialog once submission completes successfully (isSubmitting: true â†’ false)
        val wasSubmitting = remember { mutableStateOf(false) }
        LaunchedEffect(uiState.isSubmitting) {
            if (wasSubmitting.value && !uiState.isSubmitting && uiState.error == null) {
                showAddDialog = false
            }
            wasSubmitting.value = uiState.isSubmitting
        }
    }

    editingCard?.let { card ->
        EditFlashcardDialog(
            card = card,
            groupNames = groupNames,
            onSubmit = { term, definition, groupName, mediaUrl, mediaType ->
                viewModel.editFlashcard(card, term, definition, groupName, mediaUrl, mediaType)
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }

    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text(localizedText("حذف المصطلح", "Delete term"), fontWeight = FontWeight.Bold) },
            text = { Text(localizedText("سيتم حذف المصطلح من جهازك ومن المحتوى العام.", "This term will be deleted from your device and from the shared content.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFlashcardImmediately(card)
                        deletingCard = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(localizedText("حذف", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCard = null }) {
                    Text(localizedText("إلغاء", "Cancel"))
                }
            }
        )
    }

    pendingExportGroup?.let { groupName ->
        AlertDialog(
            onDismissRequest = { pendingExportGroup = null },
            title = { Text(localizedText("تصدير المجموعة الحالية", "Export current group"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("سيتم حفظ بطاقات هذه المجموعة في ملف CSV منسق:", "The cards in this group will be saved as a formatted CSV file:"))
                    AssistChip(
                        onClick = {},
                        label = { Text(groupName) }
                    )
                    Text(
                        text = localizedText("بعد المتابعة سيظهر لك اختيار مكان حفظ الملف على الجهاز.", "After you continue, you will be asked where to save the file on the device."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = groupName
                            .trim()
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            .replace("\\s+".toRegex(), "_")
                        confirmedExportGroup = groupName
                        pendingExportGroup = null
                        pendingExportFilename = "${sanitized.ifBlank { "group" }}.csv"
                    }
                ) {
                    Text(localizedText("متابعة", "Continue"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportGroup = null }) {
                    Text(localizedText("إلغاء", "Cancel"))
                }
            }
        )
    }

    if (showExportGroupPicker) {
        AlertDialog(
            onDismissRequest = { showExportGroupPicker = false },
            title = { Text(localizedText("اختر مجموعة للتصدير", "Choose a group to export"), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupNames) { groupName ->
                        AssistChip(
                            onClick = {
                                pendingExportGroup = groupName
                                showExportGroupPicker = false
                            },
                            label = { Text(groupName) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportGroupPicker = false }) {
                    Text(localizedText("إلغاء", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun GroupFilterLazyRow(
    groups: List<String>,
    selected: String?,
    onGroupSelected: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onGroupSelected(null) },
                label = { Text(localizedText("الكل", "All")) }
            )
        }
        items(groups) { group ->
            FilterChip(
                selected = selected == group,
                onClick = { onGroupSelected(group) },
                label = { Text(group) }
            )
        }
    }
}

@Composable
private fun FlashcardsHeroCard(
    totalCards: Int,
    selectedGroupName: String?
) {
    val themeTokens = EduThemeExtras.tokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        themeTokens.heroGradient
                    )
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedText("البطاقات التعليمية", "Flashcards"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (selectedGroupName.isNullOrBlank()) {
                        localizedText("مكتبتك المحلية للمصطلحات والتعريفات السريعة.", "Your local library for terms and quick definitions.")
                    } else {
                        localizedText("تستعرض الآن مجموعة $selectedGroupName.", "You are currently browsing the $selectedGroupName group.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f)
                )
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            localizedText("$totalCards بطاقة متاحة", "$totalCards cards available"),
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
                modifier = Modifier.size(58.dp),
                animated = true
            )
        }
    }
}

@Composable
fun FlashcardItem(
    card: Flashcard,
    currentUserId: String = "",
    isAdmin: Boolean = false,
    isBookmarked: Boolean = false,
    onBookmark: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSpeakTerm: (() -> Unit)? = null,
    onSpeakDefinition: (() -> Unit)? = null,
    audioUiState: FlashcardAudioUiState? = null,
    audioCountdownSeconds: Int? = null
) {
    val canManageCard = isAdmin || currentUserId == card.contributor
    val isPreparingTerm = audioUiState == FlashcardAudioUiState.PREPARING_TERM
    val isPreparingDefinition = audioUiState == FlashcardAudioUiState.PREPARING_DEFINITION
    val cardA11y = localizedText("بطاقة: ${card.term}. ${card.definition}", "Card: ${card.term}. ${card.definition}")
    val speakTermA11y = localizedText("استمع لنطق ${card.term}", "Listen to ${card.term}")
    val preparingDefinitionLabel = localizedText("جارٍ تجهيز الصوت ${audioCountdownSeconds ?: 12}ث", "Preparing audio ${audioCountdownSeconds ?: 12}s")
    val definitionAudioLabel = localizedText("صوت التعريف", "Definition audio")
    val bookmarkLabel = localizedText("حفظ", "Save")
    val removeBookmarkLabel = localizedText("إلغاء الحفظ", "Remove bookmark")
    val editLabel = localizedText("تعديل", "Edit")
    val deleteLabel = localizedText("حذف المصطلح", "Delete term")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = cardA11y
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.term,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        // Speak button next to the term
                        if (onSpeakTerm != null) {
                            IconButton(
                                onClick = onSpeakTerm,
                                enabled = !isPreparingTerm,
                                modifier = Modifier
                                    .size(28.dp)
                                    .semantics { contentDescription = speakTermA11y }
                            ) {
                                if (isPreparingTerm) {
                                    Text(
                                        text = "${audioCountdownSeconds ?: 12}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = card.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                    if (onSpeakDefinition != null) {
                        TextButton(
                            onClick = onSpeakDefinition,
                            enabled = !isPreparingDefinition,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (isPreparingDefinition) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    preparingDefinitionLabel,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(definitionAudioLabel, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Row {
                    if (onBookmark != null) {
                        IconButton(onClick = onBookmark, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) removeBookmarkLabel else bookmarkLabel,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onEdit != null && canManageCard) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = editLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onDelete != null) {
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = deleteLabel,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

    // Media preview - compact and does not dominate the card layout
            if (card.mediaType != MediaType.NONE && card.mediaUrl != null) {
                var mediaExpanded by remember { mutableStateOf(false) }

                when (card.mediaType) {
                    MediaType.IMAGE -> {
            // Thumbnail row - tapping expands to full width
                        if (!mediaExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { mediaExpanded = true }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                coil.compose.AsyncImage(
                                    model = card.mediaUrl,
                                    contentDescription = localizedText("صورة مرفقة", "Attached image"),
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    localizedText("صورة مرفقة - اضغط للعرض", "Attached image - tap to view"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Column {
                                coil.compose.AsyncImage(
                                    model = card.mediaUrl,
                                    contentDescription = localizedText("صورة مرفقة", "Attached image"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { mediaExpanded = false },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                TextButton(
                                    onClick = { mediaExpanded = false },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(localizedText("إخفاء", "Hide"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    MediaType.VIDEO -> {
            // Collapsed: small icon row - expanded: player
                        if (!mediaExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { mediaExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    localizedText("فيديو مرفق - اضغط للتشغيل", "Attached video - tap to play"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Column {
                                com.eduspecial.presentation.common.MediaPlayerView(
                                    url = card.mediaUrl,
                                    isAudio = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                                TextButton(
                                    onClick = { mediaExpanded = false },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(localizedText("إخفاء", "Hide"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    MediaType.AUDIO -> {
            // Collapsed: small icon row - expanded: audio player
                        if (!mediaExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { mediaExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    localizedText("صوت مرفق - اضغط للاستماع", "Attached audio - tap to listen"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Column {
                                com.eduspecial.presentation.common.MediaPlayerView(
                                    url = card.mediaUrl,
                                    isAudio = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = { mediaExpanded = false },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(localizedText("إخفاء", "Hide"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    else -> {}
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.groupName.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(card.groupName, style = MaterialTheme.typography.labelLarge) }
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(reviewStateDisplayName(card.reviewState.name)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFlashcardDialog(
    uiState: FlashcardsUiState,
    groupNames: List<String>,
    onTermChange: (String) -> Unit,
    onDefinitionChange: (String) -> Unit,
    onGroupNameChange: (String) -> Unit,
    onSubmit: (mediaUrl: String?, mediaType: MediaType) -> Unit,
    onDismiss: () -> Unit,
    mediaUploadViewModel: MediaUploadViewModel = hiltViewModel()
) {
    var mediaUrl by remember { mutableStateOf<String?>(null) }
    var mediaType by remember { mutableStateOf(MediaType.NONE) }

    val uploadUiState by mediaUploadViewModel.uiState.collectAsState()

    // Audio picker launcher
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { mediaUploadViewModel.uploadAudio(it) }
    }

    // Sync local mediaUrl/mediaType whenever upload completes.
    // Key on the full state snapshot so it re-fires even if the same URL is uploaded twice.
    LaunchedEffect(uploadUiState.uploadedUrl, uploadUiState.uploadedMediaType) {
        uploadUiState.uploadedUrl?.let { url ->
            mediaUrl = url
            mediaType = uploadUiState.uploadedMediaType
        }
    }

    // Derived: block submit while upload is in progress
    val isUploadInProgress = uploadUiState.isUploading
    val canSubmit = uiState.newTerm.isNotBlank() &&
            uiState.newDefinition.isNotBlank() &&
            uiState.newGroupName.isNotBlank() &&
            !uiState.isDuplicate &&
            !uiState.isSubmitting &&
            !isUploadInProgress   // â† prevent submit before upload finishes

    AlertDialog(
        onDismissRequest = {
            // Don't dismiss while uploading or submitting
            if (!isUploadInProgress && !uiState.isSubmitting) onDismiss()
        },
        title = { Text(localizedText("إضافة مصطلح جديد", "Add a new term"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.newTerm,
                    onValueChange = onTermChange,
                    label = { Text(localizedText("المصطلح (بالإنجليزية)", "Term (in English)")) },
                    isError = uiState.isDuplicate,
                    supportingText = {
                        when {
                            uiState.isDuplicate -> Text(
                                localizedText("هذا المصطلح موجود بالفعل!", "This term already exists!"),
                                color = MaterialTheme.colorScheme.error
                            )
                            uiState.isCheckingDuplicate -> Text(localizedText("جارٍ التحقق من التكرار...", "Checking for duplicates..."))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.newDefinition,
                    onValueChange = onDefinitionChange,
                    label = { Text(localizedText("التعريف", "Definition")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4
                )
                GroupNameAutocompleteField(
                    value = uiState.newGroupName,
                    onValueChange = onGroupNameChange,
                    groupNames = groupNames,
                    supportingText = localizedText("اكتب أول حرفين لاقتراح المجموعات السابقة، أو أنشئ مجموعة جديدة.", "Type the first two letters to suggest previous groups, or create a new group.")
                )

                // Media picker section
                MediaPickerSection(
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    onMediaSelected = { url, type ->
                        mediaUrl = url
                        mediaType = type
                    },
                    onMediaCleared = {
                        mediaUrl = null
                        mediaType = MediaType.NONE
                        mediaUploadViewModel.resetState()
                    },
                    onAudioPick = { audioPicker.launch("audio/*") },
                    viewModel = mediaUploadViewModel
                )

                // Upload error with retry
                uploadUiState.error?.let { error ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { mediaUploadViewModel.clearError() }) {
                            Text(localizedText("إغلاق", "Close"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                uiState.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(mediaUrl, mediaType) },
                enabled = canSubmit
            ) {
                when {
                    isUploadInProgress -> {
                        // Show upload progress in the button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(localizedText("جارٍ الرفع ${uploadUiState.uploadProgress}%", "Uploading ${uploadUiState.uploadProgress}%"))
                        }
                    }
                    uiState.isSubmitting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    else -> Text(localizedText("إرسال", "Submit"))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploadInProgress && !uiState.isSubmitting
            ) { Text(localizedText("إلغاء", "Cancel")) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFlashcardDialog(
    card: Flashcard,
    groupNames: List<String>,
    onSubmit: (term: String, definition: String, groupName: String, mediaUrl: String?, mediaType: MediaType) -> Unit,
    onDismiss: () -> Unit,
    mediaUploadViewModel: MediaUploadViewModel = hiltViewModel()
) {
    var term by remember { mutableStateOf(card.term) }
    var definition by remember { mutableStateOf(card.definition) }
    var groupName by remember { mutableStateOf(card.groupName) }
    var mediaUrl by remember { mutableStateOf(card.mediaUrl) }
    var mediaType by remember { mutableStateOf(card.mediaType) }

    val uploadUiState by mediaUploadViewModel.uiState.collectAsState()

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { mediaUploadViewModel.uploadAudio(it) }
    }

    LaunchedEffect(uploadUiState.uploadedUrl) {
        uploadUiState.uploadedUrl?.let { url ->
            mediaUrl = url
            mediaType = uploadUiState.uploadedMediaType
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("تعديل المصطلح", "Edit term"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text(localizedText("المصطلح", "Term")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text(localizedText("التعريف", "Definition")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                GroupNameAutocompleteField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    groupNames = groupNames,
                    supportingText = localizedText("يمكنك اختيار مجموعة سابقة أو كتابة اسم مجموعة جديدة.", "You can choose a previous group or type a new group name.")
                )

                MediaPickerSection(
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    onMediaSelected = { url, type ->
                        mediaUrl = url
                        mediaType = type
                    },
                    onMediaCleared = {
                        mediaUrl = null
                        mediaType = MediaType.NONE
                        mediaUploadViewModel.resetState()
                    },
                    onAudioPick = { audioPicker.launch("audio/*") },
                    viewModel = mediaUploadViewModel
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(term, definition, groupName, mediaUrl, mediaType) },
                enabled = term.isNotBlank() && definition.isNotBlank() && groupName.isNotBlank()
            ) {
                Text(localizedText("حفظ", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("إلغاء", "Cancel")) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupNameAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    groupNames: List<String>,
    supportingText: String
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val suggestions = remember(query, groupNames) {
        if (query.length < 2) {
            emptyList()
        } else {
            groupNames
                .filter { it.startsWith(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
                .take(6)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { shouldExpand ->
            expanded = shouldExpand && suggestions.isNotEmpty()
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.trim().length >= 2
            },
            label = { Text(localizedText("اسم المجموعة", "Group name")) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            supportingText = { Text(supportingText) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded && suggestions.isNotEmpty()
                )
            }
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun categoryArabicName(cat: FlashcardCategory): String = when (cat) {
    FlashcardCategory.ABA_THERAPY -> "تحليل السلوك التطبيقي"
    FlashcardCategory.AUTISM_SPECTRUM -> "طيف التوحد"
    FlashcardCategory.SENSORY_PROCESSING -> "المعالجة الحسية"
    FlashcardCategory.SPEECH_LANGUAGE -> "النطق واللغة"
    FlashcardCategory.OCCUPATIONAL_THERAPY -> "العلاج الوظيفي"
    FlashcardCategory.BEHAVIORAL_INTERVENTION -> "التدخل السلوكي"
    FlashcardCategory.INCLUSIVE_EDUCATION -> "التعليم الشامل"
    FlashcardCategory.DEVELOPMENTAL_DISABILITIES -> "الإعاقات النمائية"
    FlashcardCategory.ASSESSMENT_TOOLS -> "أدوات التقييم"
    FlashcardCategory.FAMILY_SUPPORT -> "دعم الأسرة"
}

fun reviewStateArabicName(state: String): String = when (state) {
    "NEW" -> "جديد"
    "LEARNING" -> "قيد التعلم"
    "REVIEW" -> "للمراجعة"
    "ARCHIVED" -> "متقن"
    else -> state
}

@Composable
private fun reviewStateDisplayName(state: String): String = when (state) {
    "NEW" -> localizedText("جديد", "New")
    "LEARNING" -> localizedText("قيد التعلم", "Learning")
    "REVIEW" -> localizedText("للمراجعة", "Review")
    "ARCHIVED" -> localizedText("متقن", "Mastered")
    else -> state
}
