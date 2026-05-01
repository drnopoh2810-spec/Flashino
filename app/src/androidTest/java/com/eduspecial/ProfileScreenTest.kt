package com.eduspecial

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduspecial.presentation.profile.ProfileScreen
import com.eduspecial.presentation.theme.EduSpecialTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.8 — Compose UI test for ProfileScreen:
 * avatar tap opens picker; display name validation shows error for short input.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.setContent {
            EduSpecialTheme {
                ProfileScreen(
                    navController = rememberNavController(),
                    innerPadding = PaddingValues(0.dp)
                )
            }
        }
    }

    @Test
    fun profileScreen_showsTopAppBar() {
        composeRule.onNodeWithText("الملف الشخصي").assertExists()
    }

    @Test
    fun profileScreen_showsEditNameButton() {
        // Edit icon button for display name should be present
        composeRule.onNodeWithContentDescription("تعديل الاسم").assertExists()
    }

    @Test
    fun profileScreen_editName_showsTextField() {
        composeRule.onNodeWithContentDescription("تعديل الاسم").performClick()
        composeRule.onNodeWithText("الاسم المعروض").assertExists()
    }

    @Test
    fun profileScreen_editName_shortInput_showsError() {
        composeRule.onNodeWithContentDescription("تعديل الاسم").performClick()
        // Clear the field and type a single character
        composeRule.onNodeWithText("الاسم المعروض")
            .performTextClearance()
        composeRule.onNodeWithText("الاسم المعروض")
            .performTextInput("أ")
        composeRule.onNodeWithText("حفظ").performClick()
        // Error message should appear
        composeRule.onNodeWithText("يجب أن يكون الاسم بين 2 و 50 حرفاً").assertExists()
    }

    @Test
    fun profileScreen_showsNotificationsToggle() {
        composeRule.onNodeWithText("إشعارات المراجعة").assertExists()
    }

    @Test
    fun profileScreen_showsBookmarksItem() {
        composeRule.onNodeWithText("المحفوظات").assertExists()
    }

    @Test
    fun profileScreen_showsSignOutButton() {
        composeRule.onNodeWithText("تسجيل الخروج").assertExists()
    }
}
