package com.eduspecial.core.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdManager private constructor(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "AdManager"

        @Volatile
        private var instance: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()
    private val _rewardedLoading = MutableStateFlow(false)
    val rewardedLoading: StateFlow<Boolean> = _rewardedLoading.asStateFlow()

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false
    private var isInitialized = false
    private var pendingRewardedRequest: PendingRewardedRequest? = null

    private data class PendingRewardedRequest(
        val activityRef: WeakReference<Activity>,
        val onRewardEarned: () -> Unit,
        val onUnavailable: () -> Unit,
        val onDismissed: () -> Unit
    )

    @MainThread
    fun initialize() {
        if (isInitialized || AdMobConfig.appId.isBlank()) return
        MobileAds.initialize(appContext)
        isInitialized = true
        preloadRewarded()
    }

    @MainThread
    fun preloadRewarded() {
        if (!AdMobConfig.isRewardedEnabled || isRewardedLoading || rewardedAd != null) return

        isRewardedLoading = true
        _rewardedLoading.value = true
        Log.d(TAG, "Loading rewarded ad unit=${AdMobConfig.rewardedAdUnitId}")
        RewardedAd.load(
            appContext,
            AdMobConfig.rewardedAdUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    isRewardedLoading = false
                    _rewardedLoading.value = false
                    rewardedAd = ad
                    _rewardedReady.value = true
                    pendingRewardedRequest?.let { pending ->
                        pendingRewardedRequest = null
                        val activity = pending.activityRef.get()
                        if (activity != null) {
                            showLoadedRewardedAd(
                                activity = activity,
                                ad = ad,
                                onRewardEarned = pending.onRewardEarned,
                                onUnavailable = pending.onUnavailable,
                                onDismissed = pending.onDismissed
                            )
                        } else {
                            rewardedAd = null
                            _rewardedReady.value = false
                            preloadRewarded()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: code=${error.code} message=${error.message}")
                    isRewardedLoading = false
                    _rewardedLoading.value = false
                    rewardedAd = null
                    _rewardedReady.value = false
                    pendingRewardedRequest?.let { pending ->
                        pendingRewardedRequest = null
                        pending.onUnavailable()
                    }
                }
            }
        )
    }

    @MainThread
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onUnavailable: () -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded not ready, queueing request until load completes")
            pendingRewardedRequest = PendingRewardedRequest(
                activityRef = WeakReference(activity),
                onRewardEarned = onRewardEarned,
                onUnavailable = onUnavailable,
                onDismissed = onDismissed
            )
            preloadRewarded()
            return
        }

        showLoadedRewardedAd(
            activity = activity,
            ad = ad,
            onRewardEarned = onRewardEarned,
            onUnavailable = onUnavailable,
            onDismissed = onDismissed
        )
    }

    @MainThread
    private fun showLoadedRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        onRewardEarned: () -> Unit,
        onUnavailable: () -> Unit,
        onDismissed: () -> Unit
    ) {
        Log.d(TAG, "Showing rewarded ad")
        rewardedAd = null
        _rewardedReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                preloadRewarded()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Rewarded ad failed to show: code=${adError.code} message=${adError.message}")
                preloadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity) { _: RewardItem ->
            Log.d(TAG, "Reward earned")
            onRewardEarned()
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
