package com.eduspecial.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eduspecial_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_STUDY_NOTIFICATIONS = booleanPreferencesKey("study_notifications")
        val KEY_EMAIL_NOTIFICATIONS = booleanPreferencesKey("email_notifications")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val KEY_AUTOPLAY_TTS = booleanPreferencesKey("autoplay_tts")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_DAILY_GOAL = intPreferencesKey("daily_goal_cards")
        val KEY_REMINDER_TIME = longPreferencesKey("reminder_time_millis")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_DISPLAY_NAME  = stringPreferencesKey("display_name")
        val KEY_AVATAR_URL    = stringPreferencesKey("avatar_url")
        val KEY_SUPABASE_SHADOW_OWNER_ID = stringPreferencesKey("supabase_shadow_owner_id")
        val KEY_SUPABASE_SHADOW_USER_ID = stringPreferencesKey("supabase_shadow_user_id")
        val KEY_PERMISSIONS_DONE = booleanPreferencesKey("permissions_done")
        val KEY_LIKED_QA_QUESTION_IDS = stringSetPreferencesKey("liked_qa_question_ids")
        val KEY_LIKED_QA_ANSWER_IDS = stringSetPreferencesKey("liked_qa_answer_ids")
        val KEY_DAILY_GOAL_UNLOCK_DATE = stringPreferencesKey("daily_goal_unlock_date")
        val KEY_DAILY_GOAL_UNLOCK_COUNT = intPreferencesKey("daily_goal_unlock_count")
        val KEY_DAILY_CREATE_UNLOCK_DATE = stringPreferencesKey("daily_create_unlock_date")
        val KEY_DAILY_CREATE_UNLOCK_COUNT = intPreferencesKey("daily_create_unlock_count")
    }

    val userId: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_ID] }

    val authToken: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTH_TOKEN] }

    val refreshToken: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_REFRESH_TOKEN] }

    val lastSyncTimestamp: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_SYNC] ?: 0L }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DARK_THEME] ?: false }

    val themeMode: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME_MODE] ?: "system" }

    val themePalette: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME_PALETTE] ?: "qusasa" }

    val language: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LANGUAGE] ?: AppLanguageManager.getPersistedLanguage(context) }

    val dailyGoal: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DAILY_GOAL] ?: 20 }

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ONBOARDING_DONE] ?: false }

    val reminderTimeMillis: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_REMINDER_TIME] ?: (8 * 60 * 60 * 1000L) } // default 08:00

    val displayName: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DISPLAY_NAME] }

    val userEmail: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_EMAIL] }

    val avatarUrl: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AVATAR_URL] }

    val isPermissionsDone: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PERMISSIONS_DONE] ?: false }

    val supabaseShadowOwnerId: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SUPABASE_SHADOW_OWNER_ID] }

    val supabaseShadowUserId: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SUPABASE_SHADOW_USER_ID] }

    val studyNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_STUDY_NOTIFICATIONS] ?: true }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }

    val emailNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EMAIL_NOTIFICATIONS] ?: true }

    val soundEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SOUND_ENABLED] ?: true }

    val vibrationEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_VIBRATION_ENABLED] ?: true }

    val autoPlayTts: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTOPLAY_TTS] ?: false }

    val likedQaQuestionIds: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LIKED_QA_QUESTION_IDS] ?: emptySet() }

    val likedQaAnswerIds: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LIKED_QA_ANSWER_IDS] ?: emptySet() }

    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { it[KEY_AUTH_TOKEN] = token }
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { it[KEY_REFRESH_TOKEN] = token }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun updateLastSync() {
        context.dataStore.edit { it[KEY_LAST_SYNC] = System.currentTimeMillis() }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_DARK_THEME] = enabled
            it[KEY_THEME_MODE] = if (enabled) "dark" else "light"
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit {
            it[KEY_THEME_MODE] = mode
            when (mode) {
                "dark" -> it[KEY_DARK_THEME] = true
                "light" -> it[KEY_DARK_THEME] = false
            }
        }
    }

    suspend fun setThemePalette(palette: String) {
        context.dataStore.edit { it[KEY_THEME_PALETTE] = palette }
    }

    suspend fun setLanguage(language: String) {
        val normalizedLanguage = if (language.lowercase().startsWith("en")) "en" else "ar"
        AppLanguageManager.persistLanguage(context, normalizedLanguage)
        context.dataStore.edit { it[KEY_LANGUAGE] = normalizedLanguage }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[KEY_DAILY_GOAL] = goal }
    }

    suspend fun getDailyGoalUnlockMeta(): Pair<String?, Int> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_DAILY_GOAL_UNLOCK_DATE] to (prefs[KEY_DAILY_GOAL_UNLOCK_COUNT] ?: 0)
    }

    suspend fun setDailyGoalUnlockMeta(date: String, count: Int) {
        context.dataStore.edit {
            it[KEY_DAILY_GOAL_UNLOCK_DATE] = date
            it[KEY_DAILY_GOAL_UNLOCK_COUNT] = count
        }
    }

    suspend fun getDailyCreateUnlockMeta(): Pair<String?, Int> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_DAILY_CREATE_UNLOCK_DATE] to (prefs[KEY_DAILY_CREATE_UNLOCK_COUNT] ?: 0)
    }

    suspend fun setDailyCreateUnlockMeta(date: String, count: Int) {
        context.dataStore.edit {
            it[KEY_DAILY_CREATE_UNLOCK_DATE] = date
            it[KEY_DAILY_CREATE_UNLOCK_COUNT] = count
        }
    }

    suspend fun markOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(KEY_USER_ID)
            it.remove(KEY_AUTH_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            it.remove(KEY_USER_EMAIL)
            it.remove(KEY_DISPLAY_NAME)
            it.remove(KEY_AVATAR_URL)
            it.remove(KEY_SUPABASE_SHADOW_OWNER_ID)
            it.remove(KEY_SUPABASE_SHADOW_USER_ID)
            it.remove(KEY_LIKED_QA_QUESTION_IDS)
            it.remove(KEY_LIKED_QA_ANSWER_IDS)
        }
    }

    suspend fun setReminderTime(timeMillis: Long) {
        context.dataStore.edit { it[KEY_REMINDER_TIME] = timeMillis }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name }
    }

    suspend fun setUserEmail(email: String) {
        context.dataStore.edit { it[KEY_USER_EMAIL] = email }
    }

    suspend fun setAvatarUrl(url: String) {
        context.dataStore.edit { it[KEY_AVATAR_URL] = url }
    }

    suspend fun setStudyNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STUDY_NOTIFICATIONS] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setEmailNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EMAIL_NOTIFICATIONS] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setAutoPlayTts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOPLAY_TTS] = enabled }
    }

    suspend fun saveSupabaseShadowMapping(firebaseUserId: String, supabaseUserId: String) {
        context.dataStore.edit {
            it[KEY_SUPABASE_SHADOW_OWNER_ID] = firebaseUserId
            it[KEY_SUPABASE_SHADOW_USER_ID] = supabaseUserId
        }
    }

    suspend fun markPermissionsDone() {
        context.dataStore.edit { it[KEY_PERMISSIONS_DONE] = true }
    }

    suspend fun setQuestionLiked(questionId: String, liked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LIKED_QA_QUESTION_IDS].orEmpty().toMutableSet()
            if (liked) current.add(questionId) else current.remove(questionId)
            prefs[KEY_LIKED_QA_QUESTION_IDS] = current
        }
    }

    suspend fun setAnswerLiked(answerId: String, liked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LIKED_QA_ANSWER_IDS].orEmpty().toMutableSet()
            if (liked) current.add(answerId) else current.remove(answerId)
            prefs[KEY_LIKED_QA_ANSWER_IDS] = current
        }
    }
}
