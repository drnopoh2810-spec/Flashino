package com.eduspecial.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// ─── Shared shape token ────────────────────────────────────────────────────────
/** 12dp radius used on all input fields and primary buttons for consistency. */
private val ComponentShape = RoundedCornerShape(12.dp)

// ─── PrimaryButton ─────────────────────────────────────────────────────────────
/**
 * Full-width primary action button with a subtle press-scale animation.
 * Uses [ComponentShape] (12dp) to match all input fields.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDesc: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "btn_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (contentDesc != null)
                    Modifier.semantics { this.contentDescription = contentDesc }
                else Modifier
            ),
        enabled = enabled && !isLoading,
        shape = ComponentShape,
        interactionSource = interactionSource
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ─── SecondaryButton ───────────────────────────────────────────────────────────
/**
 * Full-width outlined button with press-scale animation.
 * Uses [ComponentShape] (12dp) to match PrimaryButton.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "sec_btn_scale"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = ComponentShape,
        interactionSource = interactionSource
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

// ─── EduTextField ──────────────────────────────────────────────────────────────
/**
 * Standardized text field with:
 * - 12dp border-radius matching all buttons
 * - Subtle outline (onSurfaceVariant at 40% alpha) when unfocused
 * - Primary color border only on focus — no heavy blue border at rest
 * - Consistent error state and accessibility support
 */
@Composable
fun EduTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null)
                    Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            ),
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null) }
        },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = when {
            isError && errorMessage != null ->
                { { Text(errorMessage, color = MaterialTheme.colorScheme.error) } }
            supportingText != null ->
                { { Text(supportingText) } }
            else -> null
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        readOnly = readOnly,
        shape = ComponentShape,
        interactionSource = interactionSource,
        colors = OutlinedTextFieldDefaults.colors(
            // Unfocused: thin light-gray border (no heavy blue)
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
            // Focused: primary color border
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            // Error state
            errorBorderColor     = MaterialTheme.colorScheme.error,
            errorLabelColor      = MaterialTheme.colorScheme.error
        )
    )
}
