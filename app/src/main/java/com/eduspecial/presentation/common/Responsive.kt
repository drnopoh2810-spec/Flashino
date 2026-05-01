package com.eduspecial.presentation.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class EduWindowWidthClass {
    Compact,
    Medium,
    Expanded
}

@Immutable
data class ResponsiveLayoutInfo(
    val widthClass: EduWindowWidthClass,
    val contentMaxWidth: Dp,
    val formMaxWidth: Dp,
    val horizontalPadding: Dp,
    val useNavigationRail: Boolean,
    val isLandscape: Boolean
)

@Composable
fun rememberResponsiveLayoutInfo(): ResponsiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val widthClass = when {
        configuration.screenWidthDp >= 840 -> EduWindowWidthClass.Expanded
        configuration.screenWidthDp >= 600 -> EduWindowWidthClass.Medium
        else -> EduWindowWidthClass.Compact
    }

    return ResponsiveLayoutInfo(
        widthClass = widthClass,
        contentMaxWidth = when (widthClass) {
            EduWindowWidthClass.Compact -> screenWidth
            EduWindowWidthClass.Medium -> 720.dp
            EduWindowWidthClass.Expanded -> 960.dp
        },
        formMaxWidth = when (widthClass) {
            EduWindowWidthClass.Compact -> screenWidth
            EduWindowWidthClass.Medium -> 520.dp
            EduWindowWidthClass.Expanded -> 560.dp
        },
        horizontalPadding = when (widthClass) {
            EduWindowWidthClass.Compact -> 16.dp
            EduWindowWidthClass.Medium -> 24.dp
            EduWindowWidthClass.Expanded -> 32.dp
        },
        useNavigationRail = widthClass != EduWindowWidthClass.Compact,
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
}
