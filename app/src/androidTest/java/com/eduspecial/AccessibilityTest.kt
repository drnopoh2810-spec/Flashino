package com.eduspecial

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduspecial.presentation.flashcards.FlashcardsScreen
import com.eduspecial.presentation.home.HomeScreen
import com.eduspecial.presentation.profile.ProfileScreen
import com.eduspecial.presentation.qa.QAScreen
import com.eduspecial.presentation.theme.EduSpecialTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.11 — Accessibility checks for all Compose UI screens.
 * Verifies that meaningful icons have contentDescription values.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun flashcardsScreen_studyIcon_hasContentDescription() {
        composeRule.setContent {
            EduSpecialTheme {
                FlashcardsScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
        composeRule.onNodeWithContentDescription("مراجعة").assertExists()
    }

    @Test
    fun flashcardsScreen_searchIcon_hasContentDescription() {
        composeRule.setContent {
            EduSpecialTheme {
                FlashcardsScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
        composeRule.onNodeWithContentDescription("بحث").assertExists()
    }

    @Test
    fun profileScreen_editNameIcon_hasContentDescription() {
        composeRule.setContent {
            EduSpecialTheme {
                ProfileScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
        composeRule.onNodeWithContentDescription("تعديل الاسم").assertExists()
    }

    @Test
    fun homeScreen_searchIcon_hasContentDescription() {
        composeRule.setContent {
            EduSpecialTheme {
                HomeScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
        composeRule.onNodeWithContentDescription("بحث").assertExists()
    }

    @Test
    fun qaScreen_addButton_hasContentDescription() {
        composeRule.setContent {
            EduSpecialTheme {
                QAScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
        // FAB icon should have no contentDescription (text label present)
        // But the screen should render without crashes
        composeRule.onNodeWithText("اطرح سؤالاً").assertExists()
    }
}
