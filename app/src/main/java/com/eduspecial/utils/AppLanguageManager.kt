package com.eduspecial.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE = "language"

    fun wrap(base: Context): ContextWrapper {
        val language = getPersistedLanguage(base)
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        val localized = base.createConfigurationContext(configuration)
        return ContextWrapper(localized)
    }

    fun persistLanguage(context: Context, language: String) {
        val normalizedLanguage = normalizeLanguage(language)
        prefs(context).edit().putString(KEY_LANGUAGE, normalizedLanguage).apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalizedLanguage)
        )
    }

    fun getPersistedLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE, null)?.let(::normalizeLanguage)
            ?: getDeviceLanguage(context)
    }

    fun getCurrentResourcesLanguage(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return normalizeLanguage(locale?.language)
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getDeviceLanguage(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return normalizeLanguage(locale?.language)
    }

    private fun normalizeLanguage(language: String?): String {
        return if (language?.lowercase()?.startsWith("en") == true) "en" else "ar"
    }
}
