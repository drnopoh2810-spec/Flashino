package com.eduspecial.presentation.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.eduspecial.utils.AppLanguageManager

@Composable
fun localizedText(arabic: String, english: String): String {
    return localizedText(LocalContext.current, arabic, english)
}

fun localizedText(context: Context, arabic: String, english: String): String {
    return if (AppLanguageManager.getCurrentResourcesLanguage(context) == "en") {
        english
    } else {
        arabic
    }
}
