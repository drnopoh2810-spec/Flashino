package com.eduspecial

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduspecial.presentation.bookmarks.BookmarksScreen
import com.eduspecial.presentation.theme.EduSpecialTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * 22.9 — Compose UI test for BookmarksScreen:
 * tab switching; empty state shown when no bookmarks.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BookmarksScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.setContent {
            EduSpecialTheme {
                BookmarksScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
    }

    @Test
    fun bookmarksScreen_showsTopAppBar() {
        composeRule.onNodeWithText("المحفوظات").assertExists()
    }

    @Test
    fun bookmarksScreen_showsFlashcardsTab() {
        composeRule.onNodeWithText("بطاقات").assertExists()
    }

    @Test
    fun bookmarksScreen_showsQuestionsTab() {
        composeRule.onNodeWithText("أسئلة").assertExists()
    }

    @Test
    fun bookmarksScreen_emptyState_showsWhenNoBookmarks() {
        // With no bookmarks, empty state message should be visible
        composeRule.onNodeWithText("لا توجد بطاقات محفوظة بعد").assertExists()
    }

    @Test
    fun bookmarksScreen_tabSwitch_showsQuestionsEmptyState() {
        composeRule.onNodeWithText("أسئلة").performClick()
        composeRule.onNodeWithText("لا توجد أسئلة محفوظة بعد").assertExists()
    }
}
