package com.eduspecial.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.eduspecial.MainActivity
import com.eduspecial.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val REMINDER_WORK_NAME = "EduSpecial_StudyReminder"
        const val CHANNEL_ID = "study_reminder_channel"
    }

    fun schedule(enabled: Boolean, reminderTimeMillis: Long) {
        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
            return
        }
        val delay = calculateDelayUntilNextOccurrence(reminderTimeMillis)
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REMINDER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun showTestNotification() {
        showNotification(
            notificationId = 1002,
            title = context.getString(R.string.test_reminder_title),
            body = context.getString(R.string.test_reminder_body)
        )
    }

    fun showStudyReminderNow(dueCount: Int) {
        val body = if (dueCount > 0) {
            context.getString(R.string.study_reminder_due_body, dueCount)
        } else {
            context.getString(R.string.study_reminder_generic_body)
        }
        showNotification(
            notificationId = 1001,
            title = context.getString(R.string.study_reminder_title),
            body = body
        )
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "study")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun calculateDelayUntilNextOccurrence(reminderTimeMillis: Long): Long {
        val now = System.currentTimeMillis()
        // reminderTimeMillis is the time-of-day in millis from midnight (e.g., 8*60*60*1000 for 8am)
        val todayMidnight = now - (now % (24 * 60 * 60 * 1000L))
        var nextOccurrence = todayMidnight + reminderTimeMillis
        if (nextOccurrence <= now) {
            nextOccurrence += 24 * 60 * 60 * 1000L // schedule for tomorrow
        }
        return nextOccurrence - now
    }
}
