package com.eduspecial.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * EduSpecial shape scale — unified corner radii used across all components.
 *
 * Material 3 shape roles:
 *  extraSmall → chips, small badges
 *  small      → text fields, small cards
 *  medium     → cards, dialogs
 *  large      → bottom sheets, large cards
 *  extraLarge → full-screen sheets
 */
val EduShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ─── Named shape aliases for direct use ───────────────────────────────────────
/** 4dp — chips, small tags */
val ShapeXS = RoundedCornerShape(4.dp)
/** 8dp — text fields, small cards */
val ShapeSM = RoundedCornerShape(8.dp)
/** 12dp — standard cards */
val ShapeMD = RoundedCornerShape(12.dp)
/** 16dp — large cards, action sheets */
val ShapeLG = RoundedCornerShape(16.dp)
/** 20dp — study card, hero cards */
val ShapeXL = RoundedCornerShape(20.dp)
/** 24dp — dialogs, bottom sheets */
val ShapeXXL = RoundedCornerShape(24.dp)
/** 28dp — FAB, pill buttons */
val ShapePill = RoundedCornerShape(28.dp)
/** 50% — circular avatars, icon containers */
val ShapeCircle = RoundedCornerShape(50)
