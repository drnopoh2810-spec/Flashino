package com.eduspecial.utils

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eduspecial.data.repository.AnalyticsRepository
import com.eduspecial.data.repository.FlashcardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class StudyReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val flashcardRepository: FlashcardRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val prefs: UserPreferencesDataStore,
    private val notificationScheduler: NotificationScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationsEnabled = prefs.studyNotificationsEnabled.first()
        if (!notificationsEnabled) return Result.success()

        val dailyGoal = prefs.dailyGoal.first()
        val todayReviewed = analyticsRepository.getTodayReviewCount()
        if (todayReviewed >= dailyGoal) return Result.success()

        val dueCount = flashcardRepository.getDueCount()
        notificationScheduler.showStudyReminderNow(dueCount)

        val reminderTime = prefs.reminderTimeMillis.first()
        NotificationScheduler(context).schedule(enabled = true, reminderTimeMillis = reminderTime)
        return Result.success()
    }
}
