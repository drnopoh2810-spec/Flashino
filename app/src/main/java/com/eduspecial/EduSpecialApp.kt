package com.eduspecial

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Configuration
import com.eduspecial.utils.NotificationScheduler
import com.eduspecial.utils.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application bootstrap kept intentionally light so the first screen can draw quickly.
 */
@HiltAndroidApp
class EduSpecialApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: androidx.work.WorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        Handler(Looper.getMainLooper()).postDelayed({
            SyncWorker.schedulePeriodicSync(this)
        }, 3_000)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val reminderChannel = NotificationChannel(
                NotificationScheduler.CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(reminderChannel)
        }
    }
}
