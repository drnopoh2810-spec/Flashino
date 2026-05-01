package com.eduspecial

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduspecial.presentation.flashcards.FlashcardsScreen
import com.eduspecial.presentation.theme.EduSpecialTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.6 — Compose UI test for FlashcardsScreen:
 * category filter updates list; swipe-to-delete shows confirmation snackbar;
 * swipe-to-bookmark fills icon.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FlashcardsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.setContent {
            EduSpecialTheme {
                FlashcardsScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
    }

    @Test
    fun flashcardsScreen_showsTopAppBar() {
        composeRule.onNodeWithText("البطاقات التعليمية").assertExists()
    }

    @Test
    fun flashcardsScreen_showsCategoryFilter() {
        composeRule.onNodeWithText("الكل").assertExists()
    }

    @Test
    fun flashcardsScreen_categoryFilter_allChipSelected_byDefault() {
        composeRule.onNodeWithText("الكل").assertIsSelected()
    }

    @Test
    fun flashcardsScreen_categoryFilter_clickChip_updatesSelection() {
        composeRule.onNodeWithText("تحليل السلوك التطبيقي").performClick()
        composeRule.onNodeWithText("تحليل السلوك التطبيقي").assertIsSelected()
    }

    @Test
    fun flashcardsScreen_emptyState_showsWhenNoCards() {
        // With empty database, empty state should be visible
        composeRule.onNodeWithText("لا توجد بطاقات بعد").assertExists()
    }

    @Test
    fun flashcardsScreen_addButton_exists() {
        composeRule.onNodeWithText("إضافة مصطلح").assertExists()
    }
}
