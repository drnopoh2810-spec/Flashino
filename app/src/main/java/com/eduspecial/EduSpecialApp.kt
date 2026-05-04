package com.eduspecial

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.eduspecial.core.ads.AdManager
import com.eduspecial.utils.NotificationScheduler
import com.eduspecial.utils.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application bootstrap - Updated to ensure Config is loaded before other services.
 */
@HiltAndroidApp
class EduSpecialApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: androidx.work.WorkerFactory
    @Inject lateinit var configRepository: com.eduspecial.data.repository.ConfigRepository
    @Inject lateinit var algoliaSearchService: com.eduspecial.data.remote.search.AlgoliaSearchService
    @Inject lateinit var notificationRepository: com.eduspecial.data.repository.NotificationRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        AdManager.getInstance(this).initialize()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = configRepository.initializeConfig()
                Log.d("EduSpecialApp", "Remote Config Initialized: $success")

                algoliaSearchService.initialize()
            } catch (e: Exception) {
                Log.e("EduSpecialApp", "Initialization error: ${e.message}")
            }
        }

        SyncWorker.schedulePeriodicSync(this)
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
            val rewardedAdChannel = NotificationChannel(
                NotificationScheduler.REWARDED_AD_CHANNEL_ID,
                getString(R.string.rewarded_ad_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.rewarded_ad_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(reminderChannel, rewardedAdChannel)
            )
        }
    }
}
