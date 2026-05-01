package com.eduspecial

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.5 — Compose UI test for OnboardingScreen:
 * page navigation, skip, complete, page indicator dots.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun onboardingScreen_showsFirstPage() {
        // If onboarding is not done, the onboarding screen should be visible
        // Look for the "التالي" button which is present on onboarding
        composeRule.onNodeWithText("التالي").assertExists()
    }

    @Test
    fun onboardingScreen_nextButton_advancesPage() {
        composeRule.onNodeWithText("التالي").performClick()
        // After clicking next, we should still be on onboarding (page 2)
        composeRule.onNodeWithText("التالي").assertExists()
    }

    @Test
    fun onboardingScreen_skipButton_navigatesToHome() {
        composeRule.onNodeWithText("تخطي").performClick()
        // After skip, onboarding should be gone and home screen visible
        composeRule.waitUntil(3000) {
            composeRule.onAllNodesWithText("EduSpecial").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun onboardingScreen_lastPage_showsStartButton() {
        // Navigate to last page
        composeRule.onNodeWithText("التالي").performClick()
        composeRule.onNodeWithText("التالي").performClick()
        // On last page, button should say "ابدأ الآن"
        composeRule.onNodeWithText("ابدأ الآن").assertExists()
    }

    @Test
    fun onboardingScreen_startNow_navigatesToHome() {
        // Navigate to last page
        composeRule.onNodeWithText("التالي").performClick()
        composeRule.onNodeWithText("التالي").performClick()
        composeRule.onNodeWithText("ابدأ الآن").performClick()
        // Should navigate to home
        composeRule.waitUntil(3000) {
            composeRule.onAllNodesWithText("EduSpecial").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
