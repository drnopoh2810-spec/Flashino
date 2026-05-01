package com.eduspecial.presentation.common.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    slotKey: String
) {
    AdContainerView(
        slotKey = slotKey,
        modifier = modifier
    )
}
