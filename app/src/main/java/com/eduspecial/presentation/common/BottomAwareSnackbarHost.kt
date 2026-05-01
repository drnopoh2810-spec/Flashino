package com.eduspecial.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BottomAwareSnackbarHost(
    hostState: SnackbarHostState,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 16.dp
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
        )
    )
}
