package com.eduspecial.data.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class NotificationRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationScheduler: com.eduspecial.utils.NotificationScheduler
) {
    companion object {
        private const val TAG = "NotificationRepository"
    }

    private val settingsLock = Mutex()
    private var cachedSettings = NotificationSettings()

    fun updateFCMToken(token: String) {
        Log.d(TAG, "Remote push disabled; token ignored")
    }

    suspend fun initializeFCMToken(): String? {
        Log.d(TAG, "Remote push disabled; skipping token initialization")
        return null
    }

    suspend fun subscribeToTopic(topic: String): Boolean {
        Log.d(TAG, "Remote push disabled; skipping topic subscribe: $topic")
        return true
    }

    suspend fun unsubscribeFromTopic(topic: String): Boolean {
        Log.d(TAG, "Remote push disabled; skipping topic unsubscribe: $topic")
        return true
    }

    suspend fun getNotificationSettings(): NotificationSettings {
        return settingsLock.withLock { cachedSettings }
    }

    suspend fun updateNotificationSettings(settings: NotificationSettings): Boolean {
        settingsLock.withLock {
            cachedSettings = settings
        }
        Log.d(TAG, "Notification settings updated locally")
        return true
    }

    suspend fun scheduleStudyReminders(enabled: Boolean, time: String) {
        settingsLock.withLock {
            cachedSettings = cachedSettings.copy(
                enabled = enabled,
                studyReminders = enabled,
                reminderTime = time
            )
        }
        Log.d(TAG, "Study reminders ${if (enabled) "enabled" else "disabled"} at $time")
    }

    suspend fun sendTestNotification(): Boolean {
        notificationScheduler.showTestNotification()
        Log.d(TAG, "Displayed local test notification")
        return true
    }
}

data class NotificationSettings(
    val enabled: Boolean = true,
    val studyReminders: Boolean = true,
    val newContent: Boolean = true,
    val socialInteractions: Boolean = true,
    val achievements: Boolean = true,
    val reminderTime: String = "19:00"
)

enum class NotificationType {
    STUDY_REMINDER,
    NEW_FLASHCARD,
    NEW_QUESTION,
    ANSWER_RECEIVED,
    UPVOTE_RECEIVED,
    ACHIEVEMENT_UNLOCKED,
    GENERAL
}
