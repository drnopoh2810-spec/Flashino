package com.eduspecial.presentation.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.QAQuestion
import com.eduspecial.presentation.common.LottieEmptyState
import com.eduspecial.presentation.flashcards.FlashcardItem
import com.eduspecial.presentation.flashcards.categoryArabicName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val flashcardBookmarks by viewModel.flashcardBookmarks.collectAsState()
    val questionBookmarks by viewModel.questionBookmarks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المحفوظات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) }
                ) {
                    Text("بطاقات", modifier = Modifier.padding(vertical = 12.dp))
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) }
                ) {
                    Text("أسئلة", modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            when (selectedTab) {
                0 -> BookmarkedFlashcardsList(
                    flashcards = flashcardBookmarks,
                    innerPadding = innerPadding
                )
                1 -> BookmarkedQuestionsList(
                    questions = questionBookmarks,
                    innerPadding = innerPadding
                )
            }
        }
    }
}

@Composable
private fun BookmarkedFlashcardsList(
    flashcards: List<Flashcard>,
    innerPadding: PaddingValues
) {
    if (flashcards.isEmpty()) {
        LottieEmptyState(message = "لا توجد بطاقات محفوظة بعد")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(flashcards, key = { it.id }) { card ->
                FlashcardItem(card = card)
            }
        }
    }
}

@Composable
private fun BookmarkedQuestionsList(
    questions: List<QAQuestion>,
    innerPadding: PaddingValues
) {
    if (questions.isEmpty()) {
        LottieEmptyState(message = "لا توجد أسئلة محفوظة بعد")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(questions, key = { it.id }) { question ->
                BookmarkedQuestionCard(question = question)
            }
        }
    }
}

@Composable
private fun BookmarkedQuestionCard(question: QAQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        categoryArabicName(question.category),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}
