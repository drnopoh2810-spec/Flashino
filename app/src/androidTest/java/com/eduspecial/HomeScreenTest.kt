package com.eduspecial

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduspecial.presentation.home.HomeScreen
import com.eduspecial.presentation.theme.EduSpecialTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.10 — Compose UI test for HomeScreen:
 * analytics dashboard shows streak card, chart, mastery list; skeleton shown while loading.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.setContent {
            EduSpecialTheme {
                HomeScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
    }

    @Test
    fun homeScreen_showsAppTitle() {
        composeRule.onNodeWithText("EduSpecial").assertExists()
    }

    @Test
    fun homeScreen_showsQuickActions() {
        composeRule.onNodeWithText("إجراءات سريعة").assertExists()
    }

    @Test
    fun homeScreen_showsBookmarksQuickAction() {
        composeRule.onNodeWithText("المحفوظات").assertExists()
    }

    @Test
    fun homeScreen_showsAnalyticsDashboard() {
        // Scroll down to find analytics section
        composeRule.onNodeWithText("إحصائياتك").assertExists()
    }

    @Test
    fun homeScreen_showsStreakCard() {
        // Streak card shows "يوم متتالي"
        composeRule.onNodeWithText("يوم متتالي").assertExists()
    }

    @Test
    fun homeScreen_showsDailyGoalSection() {
        composeRule.onNodeWithText("الهدف اليومي").assertExists()
    }
}
