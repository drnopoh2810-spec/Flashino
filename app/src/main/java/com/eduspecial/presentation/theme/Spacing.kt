package com.eduspecial.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EduSpecial spacing scale — based on a 4dp grid.
 * Use these tokens instead of raw dp values to keep layouts consistent.
 *
 * Usage:
 *   Modifier.padding(EduSpacing.md)
 *   Spacer(Modifier.height(EduSpacing.lg))
 */
@Immutable
data class EduSpacingTokens(
    /** 2dp — hairline gaps, icon padding */
    val xxs: Dp = 2.dp,
    /** 4dp — tight spacing between related elements */
    val xs: Dp = 4.dp,
    /** 8dp — default small spacing */
    val sm: Dp = 8.dp,
    /** 12dp — medium-small spacing */
    val md_sm: Dp = 12.dp,
    /** 16dp — default content padding */
    val md: Dp = 16.dp,
    /** 20dp — medium-large spacing */
    val md_lg: Dp = 20.dp,
    /** 24dp — section spacing */
    val lg: Dp = 24.dp,
    /** 32dp — large section gaps */
    val xl: Dp = 32.dp,
    /** 40dp — hero/header padding */
    val xxl: Dp = 40.dp,
    /** 48dp — full-screen empty state padding */
    val xxxl: Dp = 48.dp,
    /** 64dp — top/bottom screen margins */
    val huge: Dp = 64.dp,
)

val EduSpacing = EduSpacingTokens()

val LocalEduSpacing = staticCompositionLocalOf { EduSpacingTokens() }
