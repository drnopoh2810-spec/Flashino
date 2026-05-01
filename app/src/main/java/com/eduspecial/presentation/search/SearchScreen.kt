package com.eduspecial.presentation.search

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.domain.model.SearchResult
import com.eduspecial.domain.model.SearchResultType
import com.eduspecial.presentation.common.LottieEmptyState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.eduspecial.presentation.common.localizedText
import com.eduspecial.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
        // Search bar surface — respects status bar height + adds comfortable top spacing
        Surface(
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Push content below the status bar
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = localizedText("رجوع", "Back"))
                    }
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(localizedText("ابحث عن مصطلح أو سؤال...", "Search for a term or question...")) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            // Unfocused: thin light-gray border (outlineVariant ≈ 1dp visual weight)
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            // Focused: primary color only when active
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearQuery) {
                                    Icon(Icons.Default.Clear, contentDescription = localizedText("مسح", "Clear"))
                                }
                            } else if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    )
                }
            }
        }

        // Filter chips — 12dp top spacing from search bar for breathing room
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filterType == null,
                onClick = { viewModel.setFilter(null) },
                label = { Text(localizedText("الكل", "All")) }
            )
            FilterChip(
                selected = uiState.filterType == SearchResultType.FLASHCARD,
                onClick = { viewModel.setFilter(SearchResultType.FLASHCARD) },
                leadingIcon = { Icon(Icons.Default.Style, null, Modifier.size(16.dp)) },
                label = { Text(localizedText("المصطلحات", "Terms")) }
            )
            FilterChip(
                selected = uiState.filterType == SearchResultType.QUESTION,
                onClick = { viewModel.setFilter(SearchResultType.QUESTION) },
                leadingIcon = { Icon(Icons.Default.QuestionAnswer, null, Modifier.size(16.dp)) },
                label = { Text(localizedText("الأسئلة", "Questions")) }
            )
        }

        // Results
        when {
            uiState.query.length < 2 -> SearchEmptyState()
            uiState.results.isEmpty() && !uiState.isLoading -> NoResultsState(uiState.query)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                localizedText("${uiState.results.size} نتيجة", "${uiState.results.size} results"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Show badge when results come from local search
                            if (uiState.isLocalResults) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        localizedText("نتائج محلية", "Local results"),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    items(uiState.results, key = { it.id }) { result ->
                        SearchResultCard(
                            result = result,
                            onClick = {
                                val route = when (result.type) {
                                    SearchResultType.FLASHCARD ->
                                        Screen.FlashcardsFocus.createRoute(result.id)
                                    SearchResultType.QUESTION ->
                                        Screen.QAFocus.createRoute(result.id)
                                }
                                navController.navigate(route)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (result.type == SearchResultType.FLASHCARD)
                    Icons.Default.Style
                else
                    Icons.Default.QuestionAnswer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = result.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SearchEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                null,
                Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                localizedText("ابحث في قاعدة البيانات", "Search the database"),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                localizedText("ابحث عن مصطلحات ABA والمفاهيم والأسئلة", "Search ABA terms, concepts, and questions"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    LottieEmptyState(
        message = localizedText("لا توجد نتائج لـ \"$query\"", "No results for \"$query\""),
        actionLabel = null,
        onAction = null
    )
}
